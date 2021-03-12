package com.ninecookies.wiremock.extensions.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Provides methods to construct {@link Map}s.
 *
 * @author M.Scheepers
 * @since 0.0.6
 */
public class Maps {

    /**
     * Creates a map with key type {@code <K>} and value type {@code <V>} and populates it with the specified
     * <i>entries</i>.
     *
     * @param <K> the key type.
     * @param <V> the value type.
     * @param entries the {@link Entry} to populate the {@link Map} with.
     * @return a new unmodifiable {@link Map} populated with the specified <i>entries</i>.
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mapOf(Entry<K, V>... entries) {
        Map<K, V> result = new LinkedHashMap<>();
        for (Entry<K, V> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    /**
     * Creates a map {@link Entry} with key type {@code <K>} and value type {@code <V>} for the specified <i>key</i> and
     * <i>value</i>.
     *
     * @param <K> the key type.
     * @param <V> the value type.
     * @param key the key.
     * @param value the value.
     * @return a new {@link Entry} to be used with {@link #mapOf(Entry...)}.
     */
    public static <K, V> Entry<K, V> entry(K key, V value) {
        return new SimpleEntry<>(key, value);
    }

    /**
     * Protected constructor that avoids that new instances of this utility class are accidentally created but still
     * allows this utility class to be inherited and enhanced.
     */
    protected Maps() {
    }

    private static final class SimpleEntry<K, V> implements Entry<K, V> {
        private K key;
        private V value;

        private SimpleEntry(K key, V value) {
            this.key = Objects.requireNonNull(key);
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException("value can't be modified in this implementation");
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) obj;
            return Objects.equals(key, entry.getKey()) && Objects.equals(value, entry.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }
}
