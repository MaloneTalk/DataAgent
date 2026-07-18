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
package io.github.malonetalk.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST, "Invalid request parameters."),
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "Invalid request parameters."),
    SQL_NOT_ALLOWED(
            "SQL_NOT_ALLOWED",
            HttpStatus.BAD_REQUEST,
            "The SQL statement does not meet the security requirements."),

    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "Resource not found."),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "Access is forbidden."),
    TABLE_HIDDEN(
            "TABLE_HIDDEN", HttpStatus.FORBIDDEN, "The table is hidden and cannot be queried."),
    DATASOURCE_NOT_FOUND("DATASOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "Datasource not found."),
    NO_ACTIVE_DATASOURCE(
            "NO_ACTIVE_DATASOURCE", HttpStatus.NOT_FOUND, "No active datasource is available."),
    DOMAIN_NOT_FOUND("DOMAIN_NOT_FOUND", HttpStatus.NOT_FOUND, "Domain not found."),
    MCP_SERVER_NOT_FOUND("MCP_SERVER_NOT_FOUND", HttpStatus.NOT_FOUND, "MCP server not found."),
    TABLE_SEMANTIC_NOT_FOUND(
            "TABLE_SEMANTIC_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "Table semantic metadata does not exist."),
    COLUMN_SEMANTIC_NOT_FOUND(
            "COLUMN_SEMANTIC_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "Column semantic metadata does not exist."),
    LOGICAL_RELATION_NOT_FOUND(
            "LOGICAL_RELATION_NOT_FOUND", HttpStatus.NOT_FOUND, "Logical relation does not exist."),

    DOMAIN_NAME_CONFLICT(
            "DOMAIN_NAME_CONFLICT", HttpStatus.CONFLICT, "Domain name already exists."),
    MCP_SERVER_NAME_CONFLICT(
            "MCP_SERVER_NAME_CONFLICT", HttpStatus.CONFLICT, "MCP server name already exists."),
    LOGICAL_RELATION_CONFLICT(
            "LOGICAL_RELATION_CONFLICT",
            HttpStatus.CONFLICT,
            "A logical relation already exists for the same source columns."),
    RESOURCE_IN_USE("RESOURCE_IN_USE", HttpStatus.CONFLICT, "Resource is currently in use."),
    DATA_CONFLICT(
            "DATA_CONFLICT",
            HttpStatus.CONFLICT,
            "The operation conflicts with the current data state."),

    METHOD_NOT_ALLOWED(
            "METHOD_NOT_ALLOWED",
            HttpStatus.METHOD_NOT_ALLOWED,
            "Request method is not supported."),
    UNSUPPORTED_MEDIA_TYPE(
            "UNSUPPORTED_MEDIA_TYPE",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Media type is not supported."),
    NOT_ACCEPTABLE(
            "NOT_ACCEPTABLE",
            HttpStatus.NOT_ACCEPTABLE,
            "No acceptable response media type is available."),

    SCHEMA_READ_FAILED(
            "SCHEMA_READ_FAILED",
            HttpStatus.SERVICE_UNAVAILABLE,
            "Failed to read the database schema. Please try again later."),
    DATA_SERVICE_UNAVAILABLE(
            "DATA_SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE,
            "The data service is temporarily unavailable. Please try again later."),

    OPERATION_FAILED(
            "OPERATION_FAILED",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Operation failed. Please try again later."),
    SQL_EXECUTION_FAILED(
            "SQL_EXECUTION_FAILED",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "SQL execution failed. Please try again later."),
    DATA_ACCESS_FAILED(
            "DATA_ACCESS_FAILED",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Data access failed. Please try again later."),
    INTERNAL_ERROR(
            "INTERNAL_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error. Please try again later.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
