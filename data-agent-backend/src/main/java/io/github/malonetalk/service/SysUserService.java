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
package io.github.malonetalk.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.malonetalk.model.bo.SysUserBo;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.dto.BaseBatchQueryDto;
import io.github.malonetalk.model.dto.UserCreateDto;
import io.github.malonetalk.model.dto.UserUpdateDto;

/** 用户业务：以 {@link SysUserBo} 作为领域对象，异常与校验在此层完成。 */
public interface SysUserService {

    UserContextBo selectAuthProjection(Integer userId);

    /**
     * 启动引导：无任何用户时创建初始超级管理员 admin；已有用户返回 null。
     *
     * @param initialPassword 初始密码；无用户且为空时抛 {@link IllegalStateException}（fail-closed）
     */
    SysUserBo bootstrapInitialAdmin(String initialPassword);

    SysUserBo findByUsername(String username);

    IPage<SysUserBo> page(BaseBatchQueryDto dto);

    SysUserBo create(UserCreateDto dto);

    SysUserBo update(Integer id, UserUpdateDto dto);

    void changePassword(Integer userId, String oldPassword, String newPassword);

    void resetPassword(Integer id, String newPassword);

    void updateStatus(Integer id, Integer status);
}
