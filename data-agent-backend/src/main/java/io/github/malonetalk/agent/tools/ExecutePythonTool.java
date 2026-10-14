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
package io.github.malonetalk.agent.tools;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecutePythonTool implements MarkAgentTool {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_CONCURRENT = 5;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT);

    @Tool(
            name = "execute_python",
            description =
                    """
                    Execute Python code for data analysis. \
                    Available libraries: pandas, numpy, scipy. \
                    SQL query results have already been obtained in the conversation; \
                    include the data directly in the Python code. \
                    Print analysis results to stdout using print(). \
                    Only use this when statistical computation \
                    (correlation, regression, distribution tests, etc.) cannot be done in SQL.\
                    """)
    public ToolResultBlock executePython(
            @ToolParam(
                            name = "code",
                            description =
                                    """
                                    Python code to execute for data analysis. \
                                    Must be self-contained and include any data inline.\
                                    """)
                    String code,
            @ToolParam(
                            name = "source_sql_call_ids",
                            description =
                                    "Optional IDs of successful execute_sql calls whose results"
                                            + " were used.",
                            required = false)
                    List<String> sourceSqlCallIds) {

        if (!semaphore.tryAcquire()) {
            return result("Error: too many concurrent Python executions.", false, sourceSqlCallIds);
        }
        try {
            PythonRun run = doExecute(code);
            return result(run.output(), run.success(), sourceSqlCallIds);
        } finally {
            semaphore.release();
        }
    }

    private ToolResultBlock result(String output, boolean success, List<String> sourceSqlCallIds) {
        return ToolResultBlock.of(
                TextBlock.builder().text(output).build(),
                Map.of(
                        "success",
                        success,
                        "sourceSqlCallIds",
                        sourceSqlCallIds == null ? List.of() : sourceSqlCallIds));
    }

    private PythonRun doExecute(String code) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("pyexec-");
            Path script = tmpDir.resolve("script.py");
            Files.writeString(script, code);

            var process =
                    new ProcessBuilder("python3", "-I", script.toString())
                            .directory(tmpDir.toFile())
                            .redirectErrorStream(true)
                            .start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return new PythonRun(
                        "Error: execution timed out after " + TIMEOUT_SECONDS + " seconds.", false);
            }

            String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            if (process.exitValue() != 0) {
                return new PythonRun("Error (exit " + process.exitValue() + "):\n" + output, false);
            }
            return new PythonRun(output, true);
        } catch (IOException e) {
            log.error("Python execution I/O error", e);
            return new PythonRun("Error: " + e.getMessage(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PythonRun("Error: execution interrupted.", false);
        } finally {
            if (tmpDir != null) {
                try (var walk = Files.walk(tmpDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(
                                    p -> {
                                        try {
                                            Files.deleteIfExists(p);
                                        } catch (IOException ignored) {
                                            // best-effort cleanup
                                        }
                                    });
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    private record PythonRun(String output, boolean success) {}
}
