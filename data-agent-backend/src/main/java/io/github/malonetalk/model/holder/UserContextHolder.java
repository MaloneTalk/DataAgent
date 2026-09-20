package io.github.malonetalk.model.holder;

import io.github.malonetalk.model.bo.UserContextBo;
import org.springframework.stereotype.Component;

@Component
public class UserContextHolder implements ThreadLocalHolder<UserContextBo> {

    private final ThreadLocal<UserContextBo> holder = new ThreadLocal<>();

    @Override
    public void set(UserContextBo obj) {
        holder.set(obj);
    }

    @Override
    public UserContextBo get() {
        return holder.get();
    }

    @Override
    public void clear() {
        holder.remove();
    }

    public Integer requireScopedUserId() {
        UserContextBo bo = checkAndGet();
        if(Boolean.TRUE.equals(bo.superAdmin())) {
            return null;
        }
        return bo.userId();
    }
}
