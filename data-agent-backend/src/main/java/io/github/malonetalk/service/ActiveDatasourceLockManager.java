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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ActiveDatasourceLockManager {

    private static final String LOCK_RESOURCE_KEY =
            ActiveDatasourceLockManager.class.getName() + ".RESOURCE";
    private static final String LOCK_NAME = "data-agent:datasource:active";
    private static final int LOCK_TIMEOUT_SECONDS = 10;

    private final DataSource dataSource;

    public ActiveDatasourceLockManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void acquireLock() {
        if (TransactionSynchronizationManager.getResource(LOCK_RESOURCE_KEY) != null) {
            return;
        }

        LockHandle lockHandle = openAndAcquireLock();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.bindResource(LOCK_RESOURCE_KEY, lockHandle);
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            LockHandle boundHandle =
                                    (LockHandle)
                                            TransactionSynchronizationManager
                                                    .unbindResourceIfPossible(LOCK_RESOURCE_KEY);
                            if (boundHandle != null) {
                                boundHandle.releaseAndClose();
                            }
                        }
                    });
            return;
        }

        lockHandle.releaseAndClose();
        throw new IllegalStateException(
                "Active datasource lock must be acquired within a transactional context.");
    }

    private LockHandle openAndAcquireLock() {
        try {
            Connection connection = dataSource.getConnection();
            LockHandle lockHandle = new LockHandle(connection);
            if (!lockHandle.tryAcquire()) {
                lockHandle.releaseAndClose();
                throw new IllegalStateException(
                        "Timed out while waiting for the active datasource lock.");
            }
            return lockHandle;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to acquire the active datasource lock.", e);
        }
    }

    private static final class LockHandle {
        private final Connection connection;

        private LockHandle(Connection connection) {
            this.connection = connection;
        }

        private boolean tryAcquire() throws SQLException {
            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
                statement.setString(1, LOCK_NAME);
                statement.setInt(2, LOCK_TIMEOUT_SECONDS);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() && rs.getInt(1) == 1;
                }
            }
        }

        private void releaseAndClose() {
            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, LOCK_NAME);
                statement.executeQuery();
            } catch (SQLException ignored) {
                // Ignore release failures and make best effort to close the connection.
            } finally {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // Ignore close failures on cleanup.
                }
            }
        }
    }
}
