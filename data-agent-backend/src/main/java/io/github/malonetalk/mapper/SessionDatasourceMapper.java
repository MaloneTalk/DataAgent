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

import io.github.malonetalk.dto.SessionDatasourceBinding;
import io.github.malonetalk.entity.SessionDatasource;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SessionDatasourceMapper {

    /** 首次绑定；主键冲突时保留已有绑定（锁定语义）。 */
    int insertIfAbsent(SessionDatasource sessionDatasource);

    SessionDatasource selectBySessionId(@Param("sessionId") String sessionId);

    int deleteBySessionId(@Param("sessionId") String sessionId);

    /** 列出所有绑定并关联数据源名称。 */
    List<SessionDatasourceBinding> listBindingsWithDatasourceName();
}
