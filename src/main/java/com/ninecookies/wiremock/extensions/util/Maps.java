package com.ninecookies.wiremock.extensions.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class Maps {

    @SafeVarargs
    public static <K, V> Map<K, V> mapOf(Entry<K, V>... entries) {
        Map<K, V> result = new LinkedHashMap<>();
        for (Entry<K, V> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    public static <K, V> Entry<K, V> entry(K key, V value) {
        return new SimpleEntry<>(key, value);
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
