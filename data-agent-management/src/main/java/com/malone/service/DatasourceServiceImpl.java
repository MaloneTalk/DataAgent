package com.malone.service;

import com.malone.entity.Datasource;
import com.malone.mapper.DatasourceMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DatasourceServiceImpl implements DatasourceService {

    private final DatasourceMapper dataSourceMapper;

    public DatasourceServiceImpl(DatasourceMapper dataSourceMapper) {
        this.dataSourceMapper = dataSourceMapper;
    }

    @Override
    public List<Datasource> findAll() {
        return dataSourceMapper.selectAll();
    }

    @Override
    public Datasource findById(Integer id) {
        return dataSourceMapper.selectById(id);
    }

    @Override
    public boolean save(Datasource dataSource) {
        dataSource.setCreateTime(LocalDateTime.now());
        dataSource.setUpdateTime(LocalDateTime.now());
        return dataSourceMapper.insert(dataSource) > 0;
    }

    @Override
    public boolean update(Datasource dataSource) {
        dataSource.setUpdateTime(LocalDateTime.now());
        return dataSourceMapper.update(dataSource) > 0;
    }

    @Override
    public boolean deleteById(Integer id) {
        return dataSourceMapper.deleteById(id) > 0;
    }

    @Override
    public List<Datasource> findByStatus(String status) {
        return dataSourceMapper.selectByStatus(status);
    }

    @Override
    public List<Datasource> findByType(String type) {
        return dataSourceMapper.selectByType(type);
    }
}
