package com.ninecookies.wiremock.extensions;

public class Strings {

    public static final String EMPTY = "";

    public static boolean isNullOrEmpty(String string) {
        return (string == null || string.trim().length() == 0);
    }

    public static String emptyToNull(String string) {
        if (isNullOrEmpty(string)) {
            return null;
        }
        return string;
    }

    public static String nullToEmpty(String string) {
        if (isNullOrEmpty(string)) {
            return EMPTY;
        }
        return string;
    }

    protected Strings() {
    }
}
