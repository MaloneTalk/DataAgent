package io.github.malonetalk.service;

import io.github.malonetalk.entity.Datasource;

import java.util.List;

public interface DatasourceService {

    List<Datasource> findAll();

    Datasource findById(Integer id);

    boolean save(Datasource dataSource);

    boolean update(Datasource dataSource);

    boolean deleteById(Integer id);

    List<Datasource> findByStatus(String status);

    List<Datasource> findByType(String type);
}
