/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */
package io.github.malonetalk.service;

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/** Keeps cancellation attached to the actual JDBC work, including work outliving its SSE stream. */
@Service
@Slf4j
public class ChatExecutionService {
    private final ConcurrentHashMap<String, Execution> running = new ConcurrentHashMap<>();

    public Execution start(String sessionId) {
        var execution = new Execution(sessionId);
        if (running.putIfAbsent(sessionId, execution) != null) {
            throw BusinessException.of(ErrorCode.DATA_CONFLICT, "The session is still executing.");
        }
        return execution;
    }

    public void cancel(String sessionId) {
        var execution =
                Optional.ofNullable(running.get(sessionId))
                        .orElseThrow(
                                () -> BusinessException.of(ErrorCode.SQL_TERMINATION_UNCONFIRMED));
        execution.cancel();
        try {
            execution.finished.get(10, TimeUnit.SECONDS);
            log.info("Chat cancellation confirmed; database tasks exited: sessionId={}", sessionId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BusinessException.of(
                    ErrorCode.SQL_TERMINATION_UNCONFIRMED,
                    ErrorCode.SQL_TERMINATION_UNCONFIRMED.getDefaultMessage(),
                    e);
        } catch (ExecutionException | TimeoutException e) {
            throw BusinessException.of(
                    ErrorCode.SQL_TERMINATION_UNCONFIRMED,
                    ErrorCode.SQL_TERMINATION_UNCONFIRMED.getDefaultMessage(),
                    e);
        }
    }

    public static QueryTask track(Optional<Execution> execution, PreparedStatement statement) {
        return execution
                .map(value -> value.track(statement))
                .orElseGet(() -> new QueryTask(statement, Optional.empty()));
    }

    public final class Execution implements AutoCloseable {
        private final String sessionId;
        private final Set<QueryTask> tasks = new HashSet<>();
        private final Sinks.Empty<Void> cancellation = Sinks.empty();
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private volatile boolean cancelled;
        private boolean closed;
        private boolean unconfirmed;

        private Execution(String sessionId) {
            this.sessionId = sessionId;
        }

        public Mono<Void> cancellation() {
            return cancellation.asMono();
        }

        private synchronized QueryTask track(PreparedStatement statement) {
            if (cancelled || closed) throw BusinessException.of(ErrorCode.REQUEST_CANCELLED);
            var task = new QueryTask(statement, Optional.of(this));
            tasks.add(task);
            return task;
        }

        public void cancel() {
            synchronized (this) {
                cancelled = true;
                tasks.forEach(QueryTask::cancel);
            }
            cancellation.tryEmitEmpty();
        }

        private synchronized void complete(QueryTask task) {
            unconfirmed |= task.unconfirmed;
            tasks.remove(task);
            completeIfFinished();
        }

        @Override
        public synchronized void close() {
            closed = true;
            if (!tasks.isEmpty()) {
                cancelled = true;
                tasks.forEach(QueryTask::cancel);
            }
            completeIfFinished();
        }

        public Mono<Void> finish() {
            close();
            return Mono.fromFuture(finished, true)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorMap(
                            error ->
                                    BusinessException.of(
                                            ErrorCode.SQL_TERMINATION_UNCONFIRMED,
                                            ErrorCode.SQL_TERMINATION_UNCONFIRMED
                                                    .getDefaultMessage(),
                                            error));
        }

        private void completeIfFinished() {
            if (!closed || !tasks.isEmpty()) return;
            if (unconfirmed)
                finished.completeExceptionally(
                        BusinessException.of(ErrorCode.SQL_TERMINATION_UNCONFIRMED));
            else finished.complete(null);
            running.remove(sessionId, this);
        }
    }

    public static final class QueryTask implements AutoCloseable {
        private final PreparedStatement statement;
        private final Optional<Execution> execution;
        private final CountDownLatch finished = new CountDownLatch(1);
        private CompletableFuture<Void> cancellation = CompletableFuture.completedFuture(null);
        private boolean cancellationStarted;
        private boolean unconfirmed;

        private QueryTask(PreparedStatement statement, Optional<Execution> execution) {
            this.statement = statement;
            this.execution = execution;
        }

        public boolean cancelled() {
            return execution.map(value -> value.cancelled).orElse(false)
                    || Thread.currentThread().isInterrupted();
        }

        public void checkCancelled() {
            if (cancelled()) throw BusinessException.of(ErrorCode.REQUEST_CANCELLED);
        }

        private synchronized void cancel() {
            if (cancellationStarted || finished.getCount() == 0) return;
            cancellationStarted = true;
            cancellation =
                    Mono.<Void>fromRunnable(
                                    () -> {
                                        long deadline =
                                                System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                                        try {
                                            // Repeat to cover cancellation racing with
                                            // executeQuery()
                                            // starting.
                                            do {
                                                try {
                                                    statement.cancel();
                                                } catch (SQLException e) {
                                                    log.debug("JDBC cancellation failed", e);
                                                }
                                            } while (!finished.await(100, TimeUnit.MILLISECONDS)
                                                    && System.nanoTime() < deadline);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new IllegalStateException(
                                                    "JDBC cancellation interrupted", e);
                                        }
                                    })
                            .subscribeOn(Schedulers.boundedElastic())
                            .toFuture();
        }

        public void awaitCancellation() {
            CompletableFuture<Void> pending;
            synchronized (this) {
                finished.countDown();
                pending = cancellation;
            }
            try {
                // The API wait is bounded; keep this connection until cancellation really exits.
                pending.join();
            } catch (CompletionException e) {
                unconfirmed = true;
                throw BusinessException.of(
                        ErrorCode.SQL_TERMINATION_UNCONFIRMED,
                        ErrorCode.SQL_TERMINATION_UNCONFIRMED.getDefaultMessage(),
                        e);
            }
        }

        public void unconfirmed() {
            unconfirmed = true;
        }

        @Override
        public void close() {
            finished.countDown();
            execution.ifPresent(value -> value.complete(this));
        }
    }
}
