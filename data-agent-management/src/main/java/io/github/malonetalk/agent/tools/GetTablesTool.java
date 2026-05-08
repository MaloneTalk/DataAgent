package io.github.malonetalk.agent.tools;

import io.github.malonetalk.common.StatusConstants;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.TableInfoService;
import io.agentscope.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class GetTablesTool {

    private static final Logger logger = LoggerFactory.getLogger(GetTablesTool.class);

    private final DatasourceService dataSourceService;
    private final TableInfoService tableInfoService;

    public GetTablesTool(DatasourceService dataSourceService, TableInfoService tableInfoService) {
        this.dataSourceService = dataSourceService;
        this.tableInfoService = tableInfoService;
    }

    @Tool(name = "get_tables", description = "获取数据库中的表信息，包括表名和表描述")
    public List<TableInfo> getTables() {
        List<Datasource> activeDataSources = dataSourceService.findByStatus(StatusConstants.ACTIVE);
        
        if (activeDataSources.isEmpty()) {
            return Collections.emptyList();
        }

        if (activeDataSources.size() > 1) {
            logger.warn("Found {} active data sources, using the first one. This may cause data inconsistency.", activeDataSources.size());
        }

        Datasource dataSource = activeDataSources.get(0);
        return tableInfoService.findByDatasourceId(dataSource.getId());
    }
}
