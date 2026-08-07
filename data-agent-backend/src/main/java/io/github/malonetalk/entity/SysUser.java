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
package io.github.malonetalk.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 系统用户。身份源抽象字段（idp_type/idp_user_id）本轮登录仅用 LOCAL，外部身份源对接后置。 */
@Data
public class SysUser {

    private Integer id;
    private String username;

    /** PBKDF2 哈希，格式 pbkdf2$iter$salt$hash；外部身份源用户为空。 */
    private String passwordHash;

    private String displayName;
    private Integer roleId;
    private String idpType;
    private String idpUserId;

    /** 1=启用 0=禁用。 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
