package com.ninecookies.wiremock.extensions.util;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Provides a method to set environment variables for testing purposes.
 *
 * @author M.Scheepers
 * @since 0.1.2
 */
public class SystemUtil {

    private static Map<String, String> modifiableEnv = null;

    /**
     * Set the specified key and value to the systems environment to inject test values.
     *
     * @param key the key to set.
     * @param value the value to set.
     */
    @SuppressWarnings("unchecked")
    public static void setenv(String key, String value) {
        try {
            if (modifiableEnv == null) {
                synchronized (SystemUtil.class) {
                    if (modifiableEnv == null) {
                        Map<String, String> unmodifiableEnv = System.getenv();
                        Class<?> envclass = unmodifiableEnv.getClass();
                        Field map = envclass.getDeclaredField("m");
                        map.setAccessible(true);
                        modifiableEnv = (Map<String, String>) map.get(unmodifiableEnv);
                    }
                }
            }
            modifiableEnv.put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
