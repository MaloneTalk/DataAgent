package io.github.malonetalk.mapper;

import io.github.malonetalk.entity.Datasource;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
