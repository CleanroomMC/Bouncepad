package com.cleanroommc.bouncepad.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Blackboard {

    private final Map<String, Object> backing;
    private final Map<String, List<Consumer<Object>>> callbacks;

    public Blackboard(Map<String, Object> backing) {
        this.backing = backing;
        this.callbacks = initializeCallbacks();
    }

    public Blackboard() {
        this(new HashMap<>());
    }

    public Object get(String key) {
        return this.backing.get(key);
    }

    public Object get(String key, Object defaultValue) {
        return this.backing.getOrDefault(key, defaultValue);
    }

    public Object get(String key, Supplier<Object> defaultValue) {
        return this.backing.getOrDefault(key, defaultValue.get());
    }

    public <T> T get(String key, Class<T> expected) {
        var value = get(key);
        if (expected.isInstance(value)) {
            return expected.cast(value);
        }
        throw new IllegalArgumentException("Expected class of type: %s | actual class of type: %s".formatted(expected, value.getClass()));
    }

    public <T> T get(String key, Class<T> expected, Supplier<T> defaultValue) {
        var value = get(key);
        if (value == null) {
            return defaultValue.get();
        }
        if (expected.isInstance(value)) {
            return expected.cast(value);
        }
        throw new IllegalArgumentException("Expected class of type: %s | actual class of type: %s".formatted(expected, value.getClass()));
    }

    public <T> void put(String key, T value) {
        if (this.backing.containsKey(key)) {
            return;
        }
        this.backing.put(key, value);
        if (this.callbacks != null) {
            this.callbacks.get(key).forEach(callback -> callback.accept(value));
        }
    }

    public <T> Object override(String key, T value) {
        var previous = this.backing.put(key, value);
        if (previous == null && this.callbacks != null) {
            this.callbacks.get(key).forEach(callback -> callback.accept(value));
        }
        return previous;
    }

    public Object remove(String key) {
        return this.backing.remove(key);
    }

    public <T> Object remove(String key, Class<T> expected) {
        var value = get(key);
        if (value == null) {
            return null;
        }
        if (expected.isInstance(value)) {
            return this.backing.remove(key);
        }
        throw new IllegalArgumentException("Expected class of type: %s | actual class of type: %s".formatted(expected, value.getClass()));
    }

    public <T> void callback(String name, Class<T> expected, Consumer<T> callback) {
        if (this.callbacks == null) {
            throw new UnsupportedOperationException("Callbacks not supported on this Blackboard.");
        }
        this.callbacks.computeIfAbsent(name, k -> new ArrayList<>()).add(new InstanceOfChecker(expected, callback));
    }

    public Set<String> keys() {
        return Set.copyOf(this.backing.keySet());
    }

    public Collection<Object> values() {
        return List.copyOf(this.backing.values());
    }

    protected Map<String, List<Consumer<Object>>> initializeCallbacks() {
        return new HashMap<>();
    }

    private static class InstanceOfChecker<T> implements Consumer<T> {

        private final Class<T> expected;
        private final Consumer<T> callback;

        private InstanceOfChecker(Class<T> expected, Consumer<T> callback) {
            this.expected = expected;
            this.callback = callback;
        }

        @Override
        public void accept(Object value) {
            if (this.expected.isInstance(value)) {
                this.callback.accept(this.expected.cast(value));
            }
        }

    }

}
