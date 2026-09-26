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
package io.github.malonetalk.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 系统用户。身份源抽象字段（idp_type/idp_user_id）本轮登录仅用 LOCAL，外部身份源对接后置。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUserPo extends BasePo {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    private String passwordHash;
    private String displayName;
    private Integer roleId;

    @TableField("is_super_admin")
    private Boolean superAdmin;

    private String idpType;
    private String idpUserId;
    private Integer status;
}
