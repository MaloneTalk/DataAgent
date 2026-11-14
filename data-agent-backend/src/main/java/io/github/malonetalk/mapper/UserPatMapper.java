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
import io.github.malonetalk.model.po.UserPatPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPatMapper extends AuditableMapper<UserPatPo> {

    default UserPatPo selectByTokenHash(String tokenHash) {
        // NULL 会让 MP 丢弃该条件，必须提前拒绝，避免退化为全表查询。
        if (tokenHash == null || tokenHash.isBlank()) {
            return null;
        }
        return selectOne(
                Wrappers.<UserPatPo>lambdaQuery()
                        .select(
                                UserPatPo::getId,
                                UserPatPo::getUserId,
                                UserPatPo::getName,
                                UserPatPo::getTokenPrefix,
                                UserPatPo::getTokenHash,
                                UserPatPo::getExpiresAt,
                                UserPatPo::getLastUsedAt,
                                UserPatPo::getRevokedAt,
                                UserPatPo::getCreateTime)
                        .eq(UserPatPo::getTokenHash, tokenHash));
    }
}
