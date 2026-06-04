package io.github.malonetalk.mapper;

import io.github.malonetalk.dto.DomainPageQuery;
import io.github.malonetalk.entity.DomainInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DomainInfoMapper {

    int insert(DomainInfo domainInfo);

    int update(DomainInfo domainInfo);

    int deleteByIds(@Param("ids") List<Integer> ids);

    List<DomainInfo> selectAll();

    List<DomainInfo> selectPage(
            @Param("query") DomainPageQuery query, @Param("sortDescending") boolean sortDescending);

    DomainInfo selectByName(@Param("name") String name);
}
