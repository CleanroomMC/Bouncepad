package com.cleanroommc.bouncepad;

import com.cleanroommc.bouncepad.api.Blackboard;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class InternalBlackboard implements Blackboard {

    static final InternalBlackboard INSTANCE = new InternalBlackboard();

    final Map<String, Object> map = new HashMap<>();

    Map<String, Consumer<Object>> callbacks;

    private InternalBlackboard() { }

    @Override
    public Object get(String key) {
        Objects.requireNonNull(key, "Key for the Blackboard must not be null");
        return this.map.get(key);
    }

    @Override
    public Object put(String key, Object value, boolean overwrite) {
        Objects.requireNonNull(value, "Value for the Blackboard must not be null");
        Objects.requireNonNull(key, "Key for the Blackboard must not be null");
        int pos = key.indexOf(':');
        if (pos <= 0 || pos >= key.length() - 1) {
            throw new IllegalArgumentException("Invalid key for Blackboard, for it must be in this format: namespace:path");
        }
        Object previous = this.map.get(key);
        if (overwrite) {
            this.map.put(key, value);
            if (this.callbacks != null) {
                Consumer<Object> consumer = this.callbacks.get(key);
                if (consumer != null) {
                    consumer.accept(value);
                }
                this.callbacks.remove(key);
                if (this.callbacks.isEmpty()) {
                    this.callbacks = null;
                }
            }
        }
        return previous;
    }

    @Override
    public Object remove(String key) {
        Objects.requireNonNull(key, "Key for the Blackboard must not be null");
        Object removal = this.map.remove(key);
        if (this.callbacks != null) {
            this.callbacks.remove(key);
        }
        return removal;
    }

    @Override
    public void callback(String key, Consumer<Object> consumer) {
        Objects.requireNonNull(key, "Key for the Blackboard must not be null");
        Objects.requireNonNull(consumer, "Callback consumer for the Blackboard must not be null");
        if (this.callbacks == null) {
            this.callbacks = new HashMap<>();
        }
        Consumer<Object> existing = this.callbacks.get(key);
        if (existing != null) {
            consumer = existing.andThen(consumer);
        }
        this.callbacks.put(key, consumer);
    }

}
