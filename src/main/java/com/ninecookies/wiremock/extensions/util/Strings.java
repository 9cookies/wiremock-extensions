package com.ninecookies.wiremock.extensions.util;

/**
 * Provides methods for convenient {@link String} handling.
 *
 * @author M.Scheepers
 * @since 0.0.6
 */
public class Strings {

    /**
     * An empty string {@code ""}.
     */
    public static final String EMPTY = "";

    /**
     * Indicates whether the specified <i>string</i> is {@code null} or empty after trimming.
     *
     * @param string the {@link String} to test.
     * @return {@code true} if the specified <i>string</i> is {@code null} or empty; otherwise {@code false}.
     */
    public static boolean isNullOrEmpty(String string) {
        return (string == null || string.trim().length() == 0);
    }

    /**
     * If the specified <i>string</i> is {@link #nullOrEmpty(String)} {@code null} is returned.
     *
     * @param string the {@link String} to test.
     * @return {@code null} if the <i>string</i> is {@link #isNullOrEmpty(String)}; otherwise the specified
     *         <i>string</i>.
     */
    public static String emptyToNull(String string) {
        if (isNullOrEmpty(string)) {
            return null;
        }
        return string;
    }

    /**
     * If the specified <i>string</i> is {@code null} {@link #EMPTY} is returned.
     *
     * @param string the {@link String} to test.
     * @return {@value #EMPTY} if the <i>string</i> is {@code null}; otherwise the specified <i>string</i>.
     */
    public static String nullToEmpty(String string) {
        if (isNullOrEmpty(string)) {
            return EMPTY;
        }
        return string;
    }

    /**
     * Protected constructor that avoids that new instances of this utility class are accidentally created but still
     * allows this utility class to be inherited and enhanced.
     */
    protected Strings() {
    }
}
