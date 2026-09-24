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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import io.github.malonetalk.model.po.BasePo;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * 项目基础 Mapper：约束实体为 {@link BasePo} 子类，从而复用其审计字段与逻辑删除配置。
 *
 * <p>补齐 MP 的 wrapper-only 写路径（无实体参数，{@code MetaObjectHandler} 不会触发）：
 * <ul>
 *   <li>{@code update(wrapper)}：塞入空实体，交由审计填充；
 *   <li>{@code delete(wrapper)}：先按条件查出主键，再走 {@code deleteByIds}（MP 原生带审计）。
 * </ul>
 */
public interface AuditableMapper<T extends BasePo> extends BaseMapper<T> {

    @Override
    default int update(@Param(Constants.WRAPPER) Wrapper<T> updateWrapper) {
        return update(tableInfo().newInstance(), updateWrapper);
    }

    @Override
    default int delete(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper) {
        List<T> matched = selectList(queryWrapper);
        if (matched.isEmpty()) {
            return 0;
        }
        String keyProperty = tableInfo().getKeyProperty();
        List<Object> ids = new ArrayList<>(matched.size());
        for (T row : matched) {
            Object id = SystemMetaObject.forObject(row).getValue(keyProperty);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? 0 : deleteByIds(ids);
    }

    private TableInfo tableInfo() {
        Class<?> entityClass =
                ReflectionKit.getSuperClassGenericType(getClass(), BaseMapper.class, 0);
        return TableInfoHelper.getTableInfo(entityClass);
    }
}
