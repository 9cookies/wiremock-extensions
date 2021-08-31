package com.ninecookies.wiremock.extensions.util;

import java.util.regex.Matcher;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Response;
import com.jayway.jsonpath.DocumentContext;

/**
 * Provides convenient methods to describe {@link Object}s. Also conversion is supported.
 *
 * @author M.Scheepers
 * @since 0.0.6
 */
public class Objects {

    /**
     * Utilizes wiremock's {@link Json} object to convert JSON objects from one type to another, e.g. a generic
     * {@link Parameters} to an arbitrary well known JSON object. The conversion handles unknown properties gracefully.
     *
     * @param <T> the type of the target object.
     * @param source the source {@link Object} to be converted.
     * @param destinationType the {@link Class} to convert the <i>source</i> to.
     * @return a new instance of <i>destinationType</i> populates with the values of <i>source</i>.
     */
    public static <T> T convert(Object source, Class<T> destinationType) {
        String content = Json.write(source);
        return Json.read(content, destinationType);
    }

    /**
     * Returns the first non {@code null} value of the specified values.
     * <p>
     * <b>Note</b>: if both values are {@code null} the result will also be {@code null}.
     *
     * @param <T> the type of the values.
     * @param first the first value.
     * @param second the second value.
     * @return the first value that is not {@code null} or {@code null} if both values are {@code null}.
     */
    public static <T> T coalesce(T first, T second) {
        return (first != null) ? first : second;
    }

    /**
     * Describes the specified <i>response</i> for logging purposes.
     *
     * @param response the wiremock {@link Response} to describe.
     * @return the {@link String} description of the specified <i>response</i>.
     */
    public static String describe(Response response) {
        if (response == null) {
            return null;
        }
        return "[Response] " + response.getStatus() + " -> " + response.getBodyAsString();
    }

    /**
     * Describes the specified <i>object</i> for logging purposes.
     *
     * @param object the {@link Object} to describe.
     * @return the {@link String} description of the specified <i>object</i>.
     */
    public static String describe(Object object) {
        if (object == null) {
            return null;
        }
        return "[" + object.getClass().getSimpleName() + "] -> " + object;
    }

    /**
     * Describes the specified <i>documentContext</i> for logging purposes.
     *
     * @param documentContext the {@link DocumentContext} to describe.
     * @return the {@link String} description of the specified <i>documentContext</i>.
     */
    public static String describe(DocumentContext documentContext) {
        if (documentContext == null) {
            return null;
        }
        return "[" + documentContext.getClass().getSimpleName() + "]" + documentContext.jsonString();
    }

    /**
     * Describes the specified <i>matcher</i> for logging purposes.
     *
     * @param matcher the {@link Matcher} to describe.
     * @return the {@link String} description of the specified <i>matcher</i>.
     */
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

    /**
     * Protected constructor that avoids that new instances of this utility class are accidentally created but still
     * allows this utility class to be inherited and enhanced.
     */
    protected Objects() {
    }
}
