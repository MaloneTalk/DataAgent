package io.github.malonetalk.model.holder;

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;

public interface ThreadLocalHolder<T> {

    void set(T obj);
    T get();
    void clear();

    default T checkAndGet() {
        T obj = get();
        if(obj == null) {
            throw BusinessException.of(ErrorCode.VALIDATION_FAILED);
        }
        return obj;
    }
}
