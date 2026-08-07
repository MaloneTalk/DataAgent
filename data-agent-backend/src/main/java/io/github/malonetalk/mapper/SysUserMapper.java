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

import io.github.malonetalk.common.UserContext;
import io.github.malonetalk.entity.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {

    /** 登录校验：按用户名查 LOCAL 账号（含 password_hash）。 */
    SysUser selectByUsername(@Param("username") String username);

    SysUser selectById(@Param("id") Integer id);

    /** 拦截器每次请求调用：仅取鉴权必要字段，且 status=1 才返回；禁用即时生效。 */
    UserContext selectAuthProjection(@Param("id") Integer id);

    int insert(SysUser user);

    int updatePassword(
            @Param("id") Integer id,
            @Param("passwordHash") String passwordHash,
            @Param("updateTime") java.time.LocalDateTime updateTime);

    int countAll();

    List<SysUser> selectAll();

    int update(SysUser user);

    int updateStatus(@Param("id") Integer id, @Param("status") Integer status);
}
