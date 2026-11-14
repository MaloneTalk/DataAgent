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
package io.github.malonetalk.interceptor;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import io.github.malonetalk.model.BasePoConstants;
import io.github.malonetalk.model.bo.UserContextBo;
import io.github.malonetalk.model.holder.UserContextHolder;
import io.github.malonetalk.model.po.BasePo;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * {@link BasePo} 审计字段自动填充：insert 写创建/修改人与时间，update 只写修改人与时间。
 *
 * <p>强制覆盖调用方显式设置的值（数据表审计列允许为 null）；无登录上下文（启动引导、异步线程）时
 * 用户 ID 为 null，不阻断写入。
 */
@Component
@RequiredArgsConstructor
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private final UserContextHolder userContextHolder;

    @Override
    public void insertFill(MetaObject metaObject) {
        if (isNotBasePo(metaObject)) {
            return;
        }
        Long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        setFieldValByName(BasePoConstants.CREATOR_ID, userId, metaObject);
        setFieldValByName(BasePoConstants.CREATE_TIME, now, metaObject);
        setFieldValByName(BasePoConstants.UPDATE_ID, userId, metaObject);
        setFieldValByName(BasePoConstants.UPDATE_TIME, now, metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (isNotBasePo(metaObject)) {
            return;
        }
        setFieldValByName(BasePoConstants.UPDATE_ID, currentUserId(), metaObject);
        setFieldValByName(BasePoConstants.UPDATE_TIME, LocalDateTime.now(), metaObject);
    }

    private boolean isNotBasePo(MetaObject metaObject) {
        return !(metaObject.getOriginalObject() instanceof BasePo);
    }

    private Long currentUserId() {
        UserContextBo context = userContextHolder.get();
        return context == null || context.userId() == null ? null : context.userId().longValue();
    }
}
