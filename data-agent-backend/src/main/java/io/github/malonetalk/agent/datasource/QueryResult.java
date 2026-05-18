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
package io.github.malonetalk.agent.datasource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record QueryResult(
        List<String> columns, List<Map<String, Object>> rows, int totalRows, boolean truncated) {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Columns: ").append(columns).append("\n");
        sb.append("Total rows: ").append(totalRows);
        if (truncated) {
            sb.append(" (result truncated, showing first ").append(rows.size()).append(" rows)");
        }
        sb.append("\n");
        for (Map<String, Object> row : rows) {
            sb.append(row).append("\n");
        }
        return sb.toString();
    }

    public static class Builder {

        private final List<String> columns = new ArrayList<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private int totalRows;
        private boolean truncated;

        public Builder addColumn(String columnName) {
            columns.add(columnName);
            return this;
        }

        public Builder addRow(Map<String, Object> row) {
            rows.add(row);
            return this;
        }

        public Builder newRow() {
            rows.add(new LinkedHashMap<>());
            return this;
        }

        public Builder put(String column, Object value) {
            if (rows.isEmpty()) {
                newRow();
            }
            rows.get(rows.size() - 1).put(column, value);
            return this;
        }

        public Builder totalRows(int totalRows) {
            this.totalRows = totalRows;
            return this;
        }

        public Builder truncated(boolean truncated) {
            this.truncated = truncated;
            return this;
        }

        public QueryResult build() {
            return new QueryResult(columns, rows, totalRows, truncated);
        }
    }
}
