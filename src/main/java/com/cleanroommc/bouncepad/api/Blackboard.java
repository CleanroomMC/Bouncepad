package com.cleanroommc.bouncepad.api;

import java.util.function.Consumer;

public interface Blackboard {

    Object get(String key);

    Object put(String key, Object value, boolean overwrite);

    Object remove(String key);

    void callback(String key, Consumer<Object> consumer);

    default <T> T get(Class<T> valueType, String key) {
        return valueType.cast(get(key));
    }

    default Object put(String key, Object value) {
        return put(key, value, true);
    }

    default <T> void callback(String key, Class<T> consumerType, Consumer<T> consumer) {
        this.callback(key, (Consumer<Object>) consumer);
    }

}
