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

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSessionMapper {

    /** 首次访问声明归属；主键冲突静默忽略。 */
    int insertIgnore(@Param("userId") int userId, @Param("sessionId") String sessionId);

    /** 查询指定用户是否拥有该会话，用于归属校验。 */
    boolean exists(@Param("userId") int userId, @Param("sessionId") String sessionId);

    /** 查询用户的所有会话 ID，用于 listSessions 过滤。 */
    List<String> selectSessionIdsByUserId(@Param("userId") int userId);

    /** 删除指定会话的归属记录。 */
    int deleteBySessionId(@Param("sessionId") String sessionId);

    /** 删除用户的所有归属记录。 */
    int deleteByUserId(@Param("userId") int userId);
}
