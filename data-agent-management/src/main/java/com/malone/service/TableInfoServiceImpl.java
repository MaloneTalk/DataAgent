package com.malone.service;

import com.malone.entity.TableInfo;
import com.malone.mapper.TableInfoMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TableInfoServiceImpl implements TableInfoService {

    private final TableInfoMapper tableInfoMapper;

    public TableInfoServiceImpl(TableInfoMapper tableInfoMapper) {
        this.tableInfoMapper = tableInfoMapper;
    }

    @Override
    public List<TableInfo> findAll() {
        return tableInfoMapper.selectAll();
    }

    @Override
    public TableInfo findById(Integer id) {
        return tableInfoMapper.selectById(id);
    }

    @Override
    public boolean save(TableInfo tableInfo) {
        tableInfo.setCreateTime(LocalDateTime.now());
        tableInfo.setUpdateTime(LocalDateTime.now());
        return tableInfoMapper.insert(tableInfo) > 0;
    }

    @Override
    public boolean update(TableInfo tableInfo) {
        tableInfo.setUpdateTime(LocalDateTime.now());
        return tableInfoMapper.update(tableInfo) > 0;
    }

    @Override
    public boolean deleteById(Integer id) {
        return tableInfoMapper.deleteById(id) > 0;
    }

    @Override
    public List<TableInfo> findByDatasourceId(Integer datasourceId) {
        return tableInfoMapper.selectByDatasourceId(datasourceId);
    }

    @Override
    public List<TableInfo> findByIsActive(Boolean isActive) {
        return tableInfoMapper.selectByIsActive(isActive);
    }

    @Override
    public List<TableInfo> findByDatasourceIdAndIsActive(Integer datasourceId, Boolean isActive) {
        return tableInfoMapper.selectByDatasourceIdAndIsActive(datasourceId, isActive);
    }
}
