package com.ninecookies.wiremock.extensions.util;

import java.lang.reflect.Field;
import java.util.Map;

public class SystemUtil {

    /**
     * Set the specified key and value to the systems environment to inject test values.
     * 
     * @param key the key to set.
     * @param value the value to set.
     */
    public static void setenv(String key, String value) {
        try {
            System.out.println("setting environment");
            Map<String, String> unmodifiableEnv = System.getenv();
            Class<?> envclass = unmodifiableEnv.getClass();
            Field map = envclass.getDeclaredField("m");
            map.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> modifiableEnv = (Map<String, String>) map.get(unmodifiableEnv);
            modifiableEnv.put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
