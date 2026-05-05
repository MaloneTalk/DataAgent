package com.malone.mapper;

import com.malone.entity.Datasource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DatasourceMapper {

    List<Datasource> selectAll();

    Datasource selectById(@Param("id") Integer id);

    int insert(Datasource dataSource);

    int update(Datasource dataSource);

    int deleteById(@Param("id") Integer id);

    List<Datasource> selectByStatus(@Param("status") String status);

    List<Datasource> selectByType(@Param("type") String type);
}
