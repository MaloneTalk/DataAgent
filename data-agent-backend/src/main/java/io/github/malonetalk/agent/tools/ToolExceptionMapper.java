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

import io.agentscope.core.message.ToolResultBlock;
import io.github.malonetalk.agent.datasource.SchemaReader.SchemaReadException;
import io.github.malonetalk.agent.datasource.SqlExecutor.SqlExecutionException;
import io.github.malonetalk.agent.datasource.SqlExecutor.SqlSecurityException;
import io.github.malonetalk.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

@Component
public class ToolExceptionMapper {

    public ToolResultBlock toToolResult(Exception exception) {
        return ToolResultBlock.error(resolveMessage(exception));
    }

    private String resolveMessage(Exception exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        if (exception instanceof IllegalArgumentException) {
            return exception.getMessage() == null
                    ? "Invalid request parameters."
                    : exception.getMessage();
        }
        if (exception instanceof SqlSecurityException) {
            return exception.getMessage() == null
                    ? "The SQL statement does not meet the security requirements."
                    : exception.getMessage();
        }
        if (exception instanceof SchemaReadException) {
            return "Failed to read the database schema. Please try again later.";
        }
        if (exception instanceof SqlExecutionException) {
            return "SQL execution failed. Please try again later.";
        }
        if (exception instanceof DataIntegrityViolationException) {
            return "The operation conflicts with the current data state.";
        }
        if (exception instanceof TransientDataAccessException) {
            return "The data service is temporarily unavailable. Please try again later.";
        }
        if (exception instanceof DataAccessException) {
            return "Data access failed. Please try again later.";
        }
        return "Internal server error. Please try again later.";
    }
}
