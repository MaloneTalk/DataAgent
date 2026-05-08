package io.github.malonetalk.service;

import io.github.malonetalk.entity.TableInfo;
import java.util.List;

public interface TableInfoService {

    List<TableInfo> findAll();

    TableInfo findById(Integer id);

    boolean save(TableInfo tableInfo);

    boolean update(TableInfo tableInfo);

    boolean deleteById(Integer id);

    List<TableInfo> findByDatasourceId(Integer datasourceId);

    List<TableInfo> findByIsActive(Boolean isActive);

    List<TableInfo> findByDatasourceIdAndIsActive(Integer datasourceId, Boolean isActive);
}
