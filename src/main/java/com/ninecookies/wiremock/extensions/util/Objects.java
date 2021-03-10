package com.ninecookies.wiremock.extensions.util;

import java.util.regex.Matcher;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.http.Response;
import com.jayway.jsonpath.DocumentContext;

public class Objects {

    public static <T> T convert(Object source, Class<T> destinationType) {
        String content = Json.write(source);
        return Json.read(content, destinationType);
    }

    public static String describe(Response response) {
        if (response == null) {
            return null;
        }
        return "[Response] " + response.getStatus() + " -> " + response.getBodyAsString();
    }

    public static String describe(Object object) {
        if (object == null) {
            return null;
        }
        return "[" + object.getClass().getSimpleName() + "]" + object;
    }

    public static String describe(DocumentContext documentContext) {
        if (documentContext == null) {
            return null;
        }
        return "[" + documentContext.getClass().getSimpleName() + "]" + documentContext.jsonString();
    }

    public static String describe(Matcher matcher) {
        if (matcher == null) {
            return null;
        }
        String matcherString = matcher.toString();
        StringBuilder result = new StringBuilder(matcherString.substring(0, matcherString.length() - 1));
        result.append(" groups(").append(matcher.groupCount()).append(")=");
        for (int i = 1; i < matcher.groupCount() + 1; i++) {
            if (i > 1) {
                result.append(",");
            }
            result.append("[").append(i).append("]").append(matcher.group(i));
        }
        return result.toString();
    }

    protected Objects() {
    }
}
