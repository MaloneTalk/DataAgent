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
package io.github.malonetalk.model.converter;

import io.github.malonetalk.model.bo.SysUserBo;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.po.SysUserPo;
import io.github.malonetalk.model.vo.UserInfoVo;
import io.github.malonetalk.model.vo.UserVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserConverter {

    SysUserBo toBo(SysUserPo po);

    @Mapping(target = "userId", source = "id")
    UserContextBo toContextBo(SysUserPo po);

    UserVo toVo(SysUserBo bo);

    @Mapping(target = "userId", source = "id")
    UserInfoVo toInfoVo(SysUserBo bo);

    UserInfoVo toInfoVoFromContext(UserContextBo context);
}
