package io.github.malonetalk.mapper;

import io.github.malonetalk.entity.TableInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TableInfoMapper {

    List<TableInfo> selectAll();

    TableInfo selectById(@Param("id") Integer id);

    int insert(TableInfo tableInfo);

    int update(TableInfo tableInfo);

    int deleteById(@Param("id") Integer id);

    List<TableInfo> selectByDatasourceId(@Param("datasourceId") Integer datasourceId);

    List<TableInfo> selectByIsActive(@Param("isActive") Boolean isActive);

    List<TableInfo> selectByDatasourceIdAndIsActive(
            @Param("datasourceId") Integer datasourceId, @Param("isActive") Boolean isActive);
}
