package io.github.malonetalk.agent.datasource;

public class ColumnInfo {

    private final String columnName;
    private final String typeName;
    private final int columnSize;
    private final boolean nullable;
    private final String defaultValue;
    private final boolean primaryKey;
    private final String remarks;

    public ColumnInfo(String columnName, String typeName, int columnSize, boolean nullable,
            String defaultValue, boolean primaryKey, String remarks) {
        this.columnName = columnName;
        this.typeName = typeName;
        this.columnSize = columnSize;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
        this.primaryKey = primaryKey;
        this.remarks = remarks;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getTypeName() {
        return typeName;
    }

    public int getColumnSize() {
        return columnSize;
    }

    public boolean isNullable() {
        return nullable;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public String getRemarks() {
        return remarks;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(columnName).append(" ").append(typeName);
        if (columnSize > 0) {
            sb.append("(").append(columnSize).append(")");
        }
        if (primaryKey) {
            sb.append(" PRIMARY KEY");
        }
        if (!nullable) {
            sb.append(" NOT NULL");
        }
        if (defaultValue != null && !defaultValue.isEmpty()) {
            sb.append(" DEFAULT ").append(defaultValue);
        }
        if (remarks != null && !remarks.isEmpty()) {
            sb.append(" COMMENT '").append(remarks).append("'");
        }
        return sb.toString();
    }
}
