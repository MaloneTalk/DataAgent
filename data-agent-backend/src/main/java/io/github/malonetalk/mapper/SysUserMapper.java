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

    /** 只查询身份字段和状态，账号可用性由 Service 判断。 */
    default SysUserPo selectAuthProjection(Integer userId) {
        return selectOne(
                Wrappers.<SysUserPo>lambdaQuery()
                        .select(
                                SysUserPo::getId,
                                SysUserPo::getUsername,
                                SysUserPo::getDisplayName,
                                SysUserPo::getRoleId,
                                SysUserPo::getSuperAdmin,
                                SysUserPo::getStatus)
                        .eq(SysUserPo::getId, userId));
    }
}
