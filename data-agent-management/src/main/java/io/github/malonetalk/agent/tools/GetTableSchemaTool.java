package io.github.malonetalk.agent.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.malonetalk.agent.datasource.ColumnInfo;
import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.agent.datasource.SchemaReader.SchemaReadException;
import io.github.malonetalk.common.StatusConstants;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.service.DatasourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetTableSchemaTool {

    private static final Logger logger = LoggerFactory.getLogger(GetTableSchemaTool.class);

    private final DatasourceService dataSourceService;
    private final SchemaReader schemaReader;

    public GetTableSchemaTool(DatasourceService dataSourceService, SchemaReader schemaReader) {
        this.dataSourceService = dataSourceService;
        this.schemaReader = schemaReader;
    }

    @Tool(name = "get_table_schema", description = "Get the schema information of the specified table, including column name, data type, whether it is primary key, whether it allows null, default value and column comments. This tool should be called to understand the table structure before generating SQL.")
    public String getTableSchema(
            @ToolParam(name = "table_name", description = "The table name to query schema for") String tableName) {
        List<Datasource> activeDataSources = dataSourceService.findByStatus(StatusConstants.ACTIVE);

        if (activeDataSources.isEmpty()) {
            return "No active datasource available, cannot get table schema.";
        }

        if (activeDataSources.size() > 1) {
            logger.warn("Found {} active data sources, using the first one.", activeDataSources.size());
        }

        Datasource datasource = activeDataSources.get(0);

        try {
            List<ColumnInfo> columns = schemaReader.getTableSchema(datasource, tableName);
            return formatSchema(tableName, columns);
        } catch (SchemaReadException e) {
            return "Failed to get table schema: " + e.getMessage();
        }
    }

    private String formatSchema(String tableName, List<ColumnInfo> columns) {
        if (columns.isEmpty()) {
            return "Table " + tableName + " does not exist or has no column information.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Schema of table ").append(tableName).append(":\n");

        for (ColumnInfo col : columns) {
            sb.append("  - ").append(col.toString()).append("\n");
        }

        return sb.toString();
    }
}
