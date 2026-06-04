/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */
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

    DomainInfo selectById(@Param("id") Integer id);

    DomainInfo selectByName(@Param("name") String name);
}
