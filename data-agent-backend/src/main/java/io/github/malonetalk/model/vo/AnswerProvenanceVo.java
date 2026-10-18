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
package io.github.malonetalk.model.vo;

import java.util.List;

public record AnswerProvenanceVo(
        String status,
        List<DatasourceUse> datasources,
        List<SqlUse> sqlExecutions,
        List<MetricUse> metricReferences,
        List<PythonUse> pythonExecutions,
        List<TableUse> tables,
        List<String> warnings) {

    public record DataEvidenceVo(String answerId, String sessionId, AnswerProvenanceVo evidence) {}

    public record DatasourceUse(Integer id, String name, String databaseName) {}

    public record SqlUse(
            String callId,
            String sql,
            String executedSql,
            String status,
            Integer returnedRows,
            Boolean truncated) {}

    public record MetricUse(String key, String name, String measureExpr, String filters) {}

    public record PythonUse(String callId, String code, String status) {}

    public record TableUse(
            String name, String domain, String description, List<ColumnUse> columns) {}

    public record ColumnUse(String name, String description) {}
}
