package com.ninecookies.wiremock.extensions.util;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.DocumentContext;

/**
 * Represents a placeholder for this extensions.
 *
 * @author M.Scheepers
 * @since 0.0.6
 */
public class Placeholder {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\(.*?\\)");
    private static final Predicate<String> CONTAINS_PATTERN = PLACEHOLDER_PATTERN.asPredicate();

    private final String pattern;
    private String placeholder;

    private Placeholder(String pattern) {
        this.pattern = pattern;
        this.placeholder = normalize(pattern);
    }

    private String jsonPath() {
        // change $( to $. and remove trailing )
        return placeholder.replaceFirst("\\$\\(", "\\$\\.")
                .substring(0, placeholder.length() - 1);
    }

    private String normalize(String pattern) {
        Matcher placeholder = PLACEHOLDER_PATTERN.matcher(pattern);
        if (placeholder.find()) {
            return placeholder.group();
        }
        return pattern;
    }

    /**
     * {@inheritDoc}
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return new StringBuilder("Placeholder[")
                .append("pattern=").append(pattern)
                .append(", placeholder=").append(placeholder)
                .append("]")
                .toString();
    }

    /**
     * Gets the patter as specified during construction.
     *
     * @return the patter as specified during construction.
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Gets the placeholder that was extracted from pattern during construction.
     *
     * @return the placeholder representing the pattern.
     */
    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * Gets the {@link #getPlaceholder()}'s value according to the specified {@code json}.
     *
     * @param json the JSON {@link String} to parse the placeholder value from.
     * @return the resulting value or {@code null} if {@link #getPlaceholder()} was not found.
     */
    public Object getValue(String json) {
        return getValue(Placeholders.documentContextOf(json));
    }

    /**
     * Gets the {@link #getPlaceholder()}'s value according to the specified {@code documentContext}.
     *
     * @param documentContext the {@link DocumentContext} to get the value from.
     * @return the resulting value or {@code null} if {@link #getPlaceholder()} was not found.
     */
    public Object getValue(DocumentContext documentContext) {
        if (documentContext == null) {
            return null;
        }
        return documentContext.read(jsonPath());
    }

    /**
     * Gets the {@link #getPattern()} with it's {@link #getPlaceholder()} replaced by the value gathered from the
     * specified {@code documentContext}.
     *
     * @param documentContext the {@link DocumentContext} to get the value from.
     * @return the {@link #getPattern()} with it's replaced {@link #getPlaceholder()}.
     */
    public String getSubstitute(DocumentContext documentContext) {
        String value = String.valueOf(getValue(documentContext));
        if (pattern.equals(placeholder)) {
            return value;
        }
        return pattern.replace(placeholder, value);
    }

    /**
     * Indicates whether the specified {@code string} is or contains a placeholder.
     *
     * @param string the {@link String} to check.
     * @return {@code true} if the specified {@code string} is or contains a placeholder; otherwise {@code false}.
     */
    public static boolean containsPattern(String string) {
        if (Strings.isNullOrEmpty(string)) {
            return false;
        }
        return CONTAINS_PATTERN.test(string);
    }

    /**
     * Create a new instance of {@link Placeholder} for the specified {@code pattern}.
     *
     * @param pattern the {@link String} placeholder pattern.
     * @return a new instance of {@link Placeholder};
     */
    public static Placeholder of(String pattern) {
        return new Placeholder(assertPattern(pattern));
    }

    private static String assertPattern(String pattern) {
        if (Strings.isNullOrEmpty(pattern)) {
            throw new IllegalArgumentException("'pattern' must not be null or empty");
        }
        if (!containsPattern(pattern)) {
            throw new IllegalArgumentException("pattern '" + pattern + "' is no placeholder pattern");
        }
        return pattern;
    }
}
