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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.model.po.SysUserPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends AuditableMapper<SysUserPo> {

    /** 登录/创建校验：按用户名查 LOCAL 账号（含 password_hash）。 */
    default SysUserPo selectLocalByUsername(String username) {
        return selectOne(
                Wrappers.<SysUserPo>lambdaQuery()
                        .eq(SysUserPo::getUsername, username)
                        .eq(SysUserPo::getIdpType, "LOCAL"));
    }

    /** 改密码：走实体 updateById，updater_id/update_time 交由 AuditMetaObjectHandler 填充。 */
    default int updatePassword(Integer id, String passwordHash) {
        SysUserPo patch = new SysUserPo();
        patch.setId(id);
        patch.setPasswordHash(passwordHash);
        return updateById(patch);
    }
}
