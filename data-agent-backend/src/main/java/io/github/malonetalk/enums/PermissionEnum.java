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
package io.github.malonetalk.enums;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum PermissionEnum {
    ANALYSIS_CHAT_USE("analysis:chat:use", "智能分析：使用", "智能分析"),
    ANALYSIS_SQL_EXECUTE("analysis:sql:execute", "SQL 查询：执行", "SQL 查询"),
    ANALYSIS_PYTHON_EXECUTE("analysis:python:execute", "Python 分析：执行", "Python 分析", true),
    ANALYSIS_SESSION_READ("analysis:session:read", "会话：查看", "会话"),
    ANALYSIS_SESSION_DELETE("analysis:session:delete", "会话：删除", "会话"),
    ANALYSIS_SESSION_DEBUG("analysis:session:debug", "会话：调试", "会话"),
    ANALYSIS_SESSION_READ_ALL("analysis:session:read-all", "会话：查看全部", "会话"),
    ANALYSIS_SESSION_DELETE_ALL("analysis:session:delete-all", "会话：删除全部", "会话"),
    DATA_DATASOURCE_READ("data:datasource:read", "数据源：查看", "数据源"),
    DATA_DATASOURCE_CREATE("data:datasource:create", "数据源：创建", "数据源"),
    DATA_DATASOURCE_UPDATE("data:datasource:update", "数据源：修改", "数据源"),
    DATA_DATASOURCE_DELETE("data:datasource:delete", "数据源：删除", "数据源"),
    DATA_DATASOURCE_SET_STATUS("data:datasource:set-status", "数据源：启停", "数据源"),
    DATA_SCHEMA_READ("data:schema:read", "表结构：查看", "表结构"),
    SEMANTIC_DOMAIN_READ("semantic:domain:read", "数据领域：查看", "数据领域"),
    SEMANTIC_DOMAIN_CREATE("semantic:domain:create", "数据领域：创建", "数据领域"),
    SEMANTIC_DOMAIN_UPDATE("semantic:domain:update", "数据领域：修改", "数据领域"),
    SEMANTIC_DOMAIN_DELETE("semantic:domain:delete", "数据领域：删除", "数据领域"),
    SEMANTIC_TABLE_READ("semantic:table:read", "表语义：查看", "表语义"),
    SEMANTIC_TABLE_UPDATE("semantic:table:update", "表语义：修改", "表语义"),
    SEMANTIC_TABLE_SYNC("semantic:table:sync", "表语义：同步", "表语义"),
    SEMANTIC_COLUMN_READ("semantic:column:read", "列语义：查看", "列语义"),
    SEMANTIC_COLUMN_UPDATE("semantic:column:update", "列语义：修改", "列语义"),
    SEMANTIC_RELATION_READ("semantic:relation:read", "逻辑外键：查看", "逻辑外键"),
    SEMANTIC_RELATION_CREATE("semantic:relation:create", "逻辑外键：创建", "逻辑外键"),
    SEMANTIC_RELATION_UPDATE("semantic:relation:update", "逻辑外键：修改", "逻辑外键"),
    SEMANTIC_RELATION_DELETE("semantic:relation:delete", "逻辑外键：删除", "逻辑外键"),
    SEMANTIC_METRIC_READ("semantic:metric:read", "指标口径：查看", "指标口径"),
    SEMANTIC_METRIC_CREATE("semantic:metric:create", "指标口径：创建", "指标口径"),
    SEMANTIC_METRIC_UPDATE("semantic:metric:update", "指标口径：修改", "指标口径"),
    SEMANTIC_METRIC_DELETE("semantic:metric:delete", "指标口径：删除", "指标口径"),
    ASSET_REPORT_GENERATE("asset:report:generate", "报告：生成", "报告"),
    ASSET_REPORT_READ("asset:report:read", "报告：查看", "报告"),
    ASSET_REPORT_DELETE("asset:report:delete", "报告：删除", "报告"),
    ASSET_EXPORT_CREATE("asset:export:create", "表格导出：创建", "表格导出"),
    ASSET_EXPORT_READ("asset:export:read", "表格导出：查看", "表格导出"),
    ASSET_EXPORT_DOWNLOAD("asset:export:download", "表格导出：下载", "表格导出"),
    ASSET_EXPORT_DELETE("asset:export:delete", "表格导出：删除", "表格导出"),
    ASSET_EVIDENCE_READ("asset:evidence:read", "数据证据：查看", "数据证据"),
    SYS_USER_READ("sys:user:read", "用户：查看", "用户"),
    SYS_USER_CREATE("sys:user:create", "用户：创建", "用户"),
    SYS_USER_UPDATE("sys:user:update", "用户：修改", "用户"),
    SYS_USER_SET_STATUS("sys:user:set-status", "用户：启停", "用户"),
    SYS_USER_RESET_PASSWORD("sys:user:reset-password", "用户：重置密码", "用户"),
    SYS_USER_ASSIGN_ROLE("sys:user:assign-role", "用户：分配角色", "用户", true),
    SYS_ROLE_READ("sys:role:read", "角色：查看", "角色"),
    SYS_ROLE_CREATE("sys:role:create", "角色：创建", "角色"),
    SYS_ROLE_UPDATE("sys:role:update", "角色：修改", "角色"),
    SYS_ROLE_DELETE("sys:role:delete", "角色：删除", "角色"),
    SYS_ROLE_GRANT("sys:role:grant", "角色：授权", "角色", true),
    SYS_MCP_READ("sys:mcp:read", "MCP 配置：查看", "MCP 配置"),
    SYS_MCP_UPDATE("sys:mcp:update", "MCP 配置：修改", "MCP 配置"),
    SYS_MCP_SET_STATUS("sys:mcp:set-status", "MCP 配置：启停", "MCP 配置");

    private final String code;
    private final String name;
    private final String module;
    private final boolean superAdminOnly;

    PermissionEnum(String code, String name, String module) {
        this(code, name, module, false);
    }

    PermissionEnum(String code, String name, String module, boolean superAdminOnly) {
        this.code = code;
        this.name = name;
        this.module = module;
        this.superAdminOnly = superAdminOnly;
    }

    public static Optional<PermissionEnum> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }
}
