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

import io.github.malonetalk.model.bo.UserPatBo;
import io.github.malonetalk.model.bo.UserPatCreateResultBo;
import io.github.malonetalk.model.dto.UserPatCreateDto;
import java.util.List;
import java.util.Optional;

public interface UserPatService {

    UserPatCreateResultBo create(Integer userId, UserPatCreateDto request);

    List<UserPatBo> list(Integer userId);

    void revoke(Integer userId, Long id);

    Optional<UserPatBo> authenticate(String rawToken);
}
