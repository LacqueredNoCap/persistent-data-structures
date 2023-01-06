package com.github.object.persistence.common;

public class NullWrapper {
    private final Class<?> type;

    public NullWrapper(Class<?> type) {
        this.type = type;
    }

    public Class<?> getType() {
        return type;
    }
}
