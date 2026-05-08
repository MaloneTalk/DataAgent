package io.github.malonetalk.agent.datasource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryResult {

    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final int totalRows;
    private final boolean truncated;

    public QueryResult(List<String> columns, List<Map<String, Object>> rows, int totalRows, boolean truncated) {
        this.columns = columns;
        this.rows = rows;
        this.totalRows = totalRows;
        this.truncated = truncated;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public boolean isTruncated() {
        return truncated;
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
