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

/**
 * 统一错误码定义。
 *
 * <p>每个错误码同时声明业务 code、HTTP 状态码和默认提示文案，供 HTTP 响应、SSE 错误事件和
 * agent tool 错误结果复用。
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    /** 请求参数格式或取值非法，但没有更细分的业务错误码。 */
    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST, "Invalid request parameters."),

    /** 未认证：缺少或无效的凭证（token 缺失/过期/非法/用户已禁用）。 */
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Authentication is required."),

    /** Bean Validation、绑定校验等字段级参数校验失败。 */
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "Invalid request parameters."),

    /** SQL 未通过安全检查，例如包含不允许执行的语句。 */
    SQL_NOT_ALLOWED(
            "SQL_NOT_ALLOWED",
            HttpStatus.BAD_REQUEST,
            "The SQL statement does not meet the security requirements."),

    /** 通用资源不存在，适用于静态资源或暂无专属错误码的资源。 */
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "Resource not found."),

    /** 当前请求没有权限访问目标资源或执行目标操作。 */
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "Access is forbidden."),

    /** 表已被语义层隐藏，不能用于当前查询或生成流程。 */
    TABLE_HIDDEN(
            "TABLE_HIDDEN", HttpStatus.FORBIDDEN, "The table is hidden and cannot be queried."),

    /** 当前没有可用的激活数据源。 */
    NO_ACTIVE_DATASOURCE(
            "NO_ACTIVE_DATASOURCE", HttpStatus.NOT_FOUND, "No active datasource is available."),

    /** 数据源类型不在系统支持范围内。 */
    UNSUPPORTED_DATASOURCE_TYPE(
            "UNSUPPORTED_DATASOURCE_TYPE",
            HttpStatus.BAD_REQUEST,
            "Datasource type is not supported."),

    /** 后端未打包对应数据库的 JDBC 驱动，需在 data-agent-backend/pom.xml 引入后重新构建。 */
    JDBC_DRIVER_NOT_FOUND(
            "JDBC_DRIVER_NOT_FOUND",
            HttpStatus.BAD_REQUEST,
            "The JDBC driver for this database is not bundled in the backend. "
                    + "Please add it to data-agent-backend/pom.xml and rebuild."),

    /** 逻辑关系类型非法或不受支持。 */
    INVALID_RELATION_TYPE(
            "INVALID_RELATION_TYPE", HttpStatus.BAD_REQUEST, "Relation type is invalid."),

    /** 当前数据状态与请求操作冲突。 */
    DATA_CONFLICT(
            "DATA_CONFLICT",
            HttpStatus.CONFLICT,
            "The operation conflicts with the current data state."),

    /** 会话绑定的数据源已被删除，会话无法继续使用。 */
    BOUND_DATASOURCE_UNAVAILABLE(
            "BOUND_DATASOURCE_UNAVAILABLE",
            HttpStatus.CONFLICT,
            "The datasource bound to this session no longer exists. Please start a new session."),

    /** HTTP 方法不支持。 */
    METHOD_NOT_ALLOWED(
            "METHOD_NOT_ALLOWED",
            HttpStatus.METHOD_NOT_ALLOWED,
            "Request method is not supported."),

    /** 请求 Content-Type 不受支持。 */
    UNSUPPORTED_MEDIA_TYPE(
            "UNSUPPORTED_MEDIA_TYPE",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Media type is not supported."),

    /** 请求 Accept 头无法匹配可返回的媒体类型。 */
    NOT_ACCEPTABLE(
            "NOT_ACCEPTABLE",
            HttpStatus.NOT_ACCEPTABLE,
            "No acceptable response media type is available."),

    /** 读取数据库 schema 失败，通常是数据源连接或元数据读取异常。 */
    SCHEMA_READ_FAILED(
            "SCHEMA_READ_FAILED",
            HttpStatus.SERVICE_UNAVAILABLE,
            "Failed to read the database schema. Please try again later."),

    /** 模型供应商配置缺失或当前供应商不可用。 */
    MODEL_PROVIDER_UNAVAILABLE(
            "MODEL_PROVIDER_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE,
            "Model provider is unavailable."),

    /** 数据服务暂时不可用，通常用于瞬时数据库访问异常。 */
    DATA_SERVICE_UNAVAILABLE(
            "DATA_SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE,
            "The data service is temporarily unavailable. Please try again later."),

    /** 通用操作失败，适用于写入失败等未细分的服务端业务操作失败。 */
    OPERATION_FAILED(
            "OPERATION_FAILED",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Operation failed. Please try again later."),

    /** SQL 执行失败。 */
    SQL_EXECUTION_FAILED(
            "SQL_EXECUTION_FAILED",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "SQL execution failed. Please try again later."),

    /** 数据访问失败，通常用于非瞬时数据库异常。 */
    DATA_ACCESS_FAILED(
            "DATA_ACCESS_FAILED",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Data access failed. Please try again later."),

    /** 未被识别或未被业务化处理的服务端异常。 */
    INTERNAL_ERROR(
            "INTERNAL_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error. Please try again later.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
