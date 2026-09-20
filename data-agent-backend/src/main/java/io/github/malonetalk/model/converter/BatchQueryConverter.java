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

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.malonetalk.model.vo.BatchQueryVo;

/** MyBatis-Plus 分页对象到统一分页 VO 的转换（与具体业务无关）。 */
public final class BatchQueryConverter {

    private BatchQueryConverter() {}

    public static <T> BatchQueryVo<T> toVo(IPage<T> page) {
        return new BatchQueryVo<>(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                (int) page.getPages(),
                page.getCurrent() > 1,
                page.getCurrent() < page.getPages(),
                page.getRecords());
    }
}
