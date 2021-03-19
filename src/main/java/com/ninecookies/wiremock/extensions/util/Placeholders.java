package com.ninecookies.wiremock.extensions.util;

import static com.ninecookies.wiremock.extensions.util.Objects.describe;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.Json;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Configuration.ConfigurationBuilder;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

/**
 * Provides convenient methods to parse, populate and replace placeholders in JSON strings.
 *
 * @author M.Scheepers
 * @since 0.0.6
 */
public class Placeholders {
    private static final Logger LOG = LoggerFactory.getLogger(Placeholders.class);
    private static final UnaryOperator<String> QUOTES = s -> String.format("\"%s\"", s);
    static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\(.*?\\)");

    // visible for testing
    static final Pattern KEYWORD_PATTERN = Pattern.compile("\\$\\(!(" +
            Stream.of(Keyword.keywords()).map(Keyword::keyword).collect(Collectors.joining("|"))
            + ")(.*)\\)");

    private static final ConfigurationBuilder JSON_CONTEXT_CONFIGURATION_BUILDER = Configuration.builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
            .options(Option.SUPPRESS_EXCEPTIONS);

    /**
     * Replaces all placeholders in the specified <i>jsonToTransform</i> with the related values looked up in the
     * specified <i>placeholderSource</i>.
     *
     * @param placeholderSource the placeholder source JSON string to look up values.
     * @param jsonToTransform the template JSON string containing the placeholders.
     * @return the JSON result of the template with placeholders replaced by their related values.
     */
    public static String transformJson(String placeholderSource, String jsonToTransform) {
        return transformJson(documentContextOf(placeholderSource), jsonToTransform);
    }

    /**
     * Replaces all placeholders in the specified <i>jsonToTransform</i> with the related values looked up in the
     * specified <i>placeholderSource</i>.
     *
     * @param placeholderSource the placeholder source {@link DocumentContext}to look up values.
     * @param jsonToTransform the template JSON string containing the placeholders.
     * @return the JSON result of the template with placeholders replaced by their related values.
     */
    public static String transformJson(DocumentContext placeholderSource, String jsonToTransform) {
        Map<String, Object> responsePlaceholders = Placeholders.parseJsonBody(jsonToTransform);
        Placeholders.parsePlaceholderValues(responsePlaceholders, placeholderSource);
        return Placeholders.replaceValuesInJson(responsePlaceholders, jsonToTransform);
    }

    /**
     * Splits the specified <i>url</i> by {@code /} and returns a list of URL parts.
     *
     * @param url the {@link String} URL to split.
     * @return a {@link List} of URL parts.
     */
    public static List<String> splitUrl(String url) {
        return Stream.of(url.split("/"))
                .filter(s -> !Strings.isNullOrEmpty(s))
                .collect(Collectors.toList());
    }

    /**
     * Checks whether the provided {@code value} is a keyword pattern and if so returns the keyword generated value.
     *
     * @param value value to be checked.
     * @return a keyword related value if the specified {@code value} is a keyword pattern; otherwise the value itself.
     */
    public static String transformValue(String value) {
        if (value == null) {
            return null;
        }
        Matcher isKey = KEYWORD_PATTERN.matcher(value);
        if (isKey.matches()) {
            LOG.debug(describe(isKey));
            Keyword keyword = Keyword.of(isKey.group(1));
            return String.valueOf(keyword.value(isKey.group(2)));
        }
        return value;
    }

    /**
     * Replaces placeholders and keywords in the specified {@code urlToTransform} by values found in the specified
     * {@code placeholderSource}. If the placeholder is contained in a query string part of the URL it's value will be
     * URL encoded.
     *
     * @param placeholderSource the {@link DocumentContext} to look for placeholder values.
     * @param urlToTransform the URL to transform
     * @return the transformed URL.
     */
    public static String transformUrl(DocumentContext placeholderSource, String urlToTransform) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(urlToTransform);
        while (matcher.find()) {
            String placeholder = matcher.group();
            if (placeholders.containsKey(placeholder)) {
                continue;
            }
            placeholders.put(placeholder, populatePlaceholder(placeholder, placeholderSource));
        }
        return replaceValuesInUrl(placeholders, urlToTransform);
    }

    /**
     * Creates a {@link DocumentContext} for the specified {@code json} string.
     *
     * @param json the JSON {@link String} to create the {@link DocumentContext} for.
     * @return the {@link DocumentContext} for the specified {@code json} string or {@code null} if {@code json} is
     *         {@code null} or empty ({@code ""}).
     */
    public static DocumentContext documentContextOf(String json) {
        DocumentContext result = null;
        if (json != null && json.trim().length() > 0) { // ? PARSE_CONTEXT.parse(json) : null;
            result = JsonPath.parse(json, JSON_CONTEXT_CONFIGURATION_BUILDER.build());
        }
        LOG.debug("documentContextOf('{}') -> '{}'", json, describe(result));
        return result;
    }

    /**
     * Parses the specified {@code json} for placeholder patterns.<br>
     * Note: placeholders for keywords will get their values immediately.
     *
     * @param json the JSON {@link String} that may contain placeholders.
     * @return a {@link Map} containing entries for all found placeholders.
     */
    private static Map<String, Object> parseJsonBody(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(json);
        while (matcher.find()) {
            String placeholder = matcher.group();
            if (result.containsKey(placeholder)) {
                continue;
            }
            result.put(placeholder, populatePlaceholder(placeholder));
        }
        LOG.debug("parseJsonBody('{}') -> '{}'", json, result);
        return result;
    }

    /**
     * Traverses the specified {@code placeholders} and parses the specified {@code documentContext} for each
     * placeholder who's value is {@code null}.
     *
     * @param placeholders the {@link Map} of placeholders to parse values for.
     * @param documentContext the JSON {@link DocumentContext} to look for values.
     */
    private static void parsePlaceholderValues(Map<String, Object> placeholders, DocumentContext documentContext) {
        // if placeholders is null or empty or all values are set we are already done
        if (placeholders == null || placeholders.isEmpty() || !placeholders.containsValue(null)) {
            return;
        }

        for (Entry<String, Object> placeholder : placeholders.entrySet()) {
            // just look for placeholders who don't have a value yet
            if (placeholder.getValue() == null) {
                placeholder.setValue(populatePlaceholder(placeholder.getKey(), documentContext));
            }
        }
        LOG.debug("parsePlaceholderValues('{}', {})", placeholders, describe(documentContext));
    }

    private static String replaceValuesInJson(Map<String, Object> placeholders, String json) {
        String result = json;
        for (Entry<String, Object> placeholder : placeholders.entrySet()) {
            String pattern = placeholder.getKey();
            String value = String.valueOf(placeholder.getValue());
            String quotedPattern = QUOTES.apply(pattern);
            String jsonValue = Json.write(placeholder.getValue());
            // first replace all occurrences of "$(property.path)" with it's JSON value
            // and then check for in string replacements like "arbitrary text with $(embedded) placeholder"
            result = result.replace(quotedPattern, jsonValue).replace(pattern, value);
        }
        LOG.debug("replaceValuesInJson('{}', '{}') -> '{}'", placeholders, json, result);
        return result;
    }

    private static Object populatePlaceholder(String pattern) {
        return populatePlaceholder(pattern, null);
    }

    private static Object populatePlaceholder(String pattern, DocumentContext documentContext) {
        Object result = null;
        Matcher isKey = KEYWORD_PATTERN.matcher(pattern);
        if (isKey.find()) {
            LOG.debug(describe(isKey));
            Keyword keyword = Keyword.of(isKey.group(1));
            result = keyword.value(isKey.group(2));
        } else if (documentContext != null) {
            Placeholder placeholder = Placeholder.of(pattern);
            result = placeholder.getValue(documentContext);
        }
        LOG.debug("populatePlaceholder('{}', '{}') -> '{}'", pattern, describe(documentContext), describe(result));
        return result;
    }

    private static String replaceValuesInUrl(Map<String, Object> placeholders, String urlToTransform) {
        for (String key : placeholders.keySet()) {
            String value = String.valueOf(placeholders.get(key));
            int queryStringStart = urlToTransform.indexOf('?');
            int keyStart = urlToTransform.indexOf(key);
            if (queryStringStart > 0 && keyStart > queryStringStart) {
                value = encodeValue(value);
            }
            urlToTransform = urlToTransform.replace(key, value);
        }
        return urlToTransform;
    }

    private static String encodeValue(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // visible for testing
    static abstract class Keyword {

        public abstract String keyword();

        public abstract Object value(String arguments);

        private static final Random RANDOM_GENERATOR = new Random();
        static final Pattern HAS_BOUNDS = Pattern
                .compile("\\[([\\+0-9 ]+)\\]|\\[([\\-\\+0-9 ]+),([\\-\\+0-9 ]+)\\]");
        private static final Pattern IS_CALCULATED = Pattern.compile("\\.plus\\[([HhMmSs]{1})([0-9\\-\\+]+)\\]");
        private static final Pattern HAS_ARGUMENT = Pattern.compile("\\[([A-Z_]+)\\]");

        private static ChronoUnit stringToChronoUnit(String unit) {
            switch (unit.toLowerCase(Locale.ROOT)) {
                case "h":
                    return ChronoUnit.HOURS;
                case "m":
                    return ChronoUnit.MINUTES;
                case "s":
                    return ChronoUnit.SECONDS;
                default:
                    throw new IllegalArgumentException("Invalid unit for duration '" + unit + "'.");
            }
        }

        @SuppressWarnings("unchecked")
        private static <T extends Temporal> T calculateIfRequired(String pattern, T temporal) {
            Matcher arguments = IS_CALCULATED.matcher(pattern);
            if (arguments.find()) {
                return (T) temporal.plus(Duration.of(Long.parseLong(arguments.group(2)),
                        stringToChronoUnit(arguments.group(1))));
            }
            if (pattern.startsWith(".plus[")) {
                // unmatched calculation pattern
                throw new IllegalArgumentException("invalid time calcuation pattern: '" + pattern + "'");
            }
            return temporal;
        }

        private static final Function<String, OffsetDateTime> OFFSET_DATE_TIME_PROVIDER = s -> {
            return calculateIfRequired(s, OffsetDateTime.now());
        };

        private static final Function<String, Instant> INSTANT_PROVIDER = s -> {
            return calculateIfRequired(s, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        };

        private static final Function<String, String> ENVIRONMENT_PROVIDER = s -> {
            Matcher argument = HAS_ARGUMENT.matcher(s);
            if (!argument.find()) {
                throw new IllegalArgumentException("missing environment key: '" + s + "'");
            }
            String envKey = argument.group(1);
            return System.getenv(envKey);
        };

        private static final Function<String, Integer> RANDOM_PROVIDER = s -> {
            Matcher argument = HAS_BOUNDS.matcher(s);
            if (argument.find()) {
                if (argument.group(1) != null) {
                    // only max provided
                    // Note: nextInt(bound) : the upper bound (exclusive). Must be positive.
                    int max = Integer.parseInt(argument.group(1).trim()) + 1;
                    return RANDOM_GENERATOR.nextInt(max);
                }
                // must be min and max provided otherwise
                int min = Integer.parseInt(argument.group(2).trim());
                int max = Integer.parseInt(argument.group(3).trim());
                if (min > max) {
                    throw new IllegalArgumentException("invalid bounds: min '" + min + "' >= max '" + max + "'");
                }
                return RANDOM_GENERATOR.nextInt((max + 1) - min) + min;
            }
            // something invalid provided
            if (s.contains("[")) {
                throw new IllegalArgumentException("invalid arguments for $(!Random): " + s);
            }
            return RANDOM_GENERATOR.nextInt();
        };

        private static final Keyword ENV = new SimpleKeyword("ENV", s -> ENVIRONMENT_PROVIDER.apply(s));
        private static final Keyword UUID = new SimpleKeyword("UUID", s -> java.util.UUID.randomUUID().toString());
        private static final Keyword RANDOM = new SimpleKeyword("Random", s -> RANDOM_PROVIDER.apply(s));
        private static final Keyword INSTANT = new SimpleKeyword("Instant", s -> INSTANT_PROVIDER.apply(s).toString());
        private static final Keyword TIMESTAMP = new SimpleKeyword("Timestamp",
                s -> INSTANT_PROVIDER.apply(s).toEpochMilli());
        private static final Keyword OFFSET_DATE_TIME = new SimpleKeyword("OffsetDateTime",
                s -> OFFSET_DATE_TIME_PROVIDER.apply(s).toString());
        private static final Map<String, Keyword> VALUES = Collections.unmodifiableMap(Stream
                .of(UUID, RANDOM, INSTANT, TIMESTAMP, OFFSET_DATE_TIME, ENV)
                .collect(Collectors.toMap(Keyword::keyword, k -> k)));

        private static Keyword[] keywords() {
            return VALUES.values().toArray(new Keyword[VALUES.size()]);
        }

        // visible for testing
        static Keyword of(String key) {
            return VALUES.get(key);
        }

        private static final class SimpleKeyword extends Keyword {
            private String keyword;
            private Function<String, Object> valueProvider;

            SimpleKeyword(String keyword, Function<String, Object> valueProvider) {
                this.keyword = keyword;
                this.valueProvider = valueProvider;
            }

            @Override
            public String keyword() {
                return keyword;
            }

            @Override
            public Object value(String arguments) {
                return valueProvider.apply(arguments);
            }
        }
    }

    /**
     * Protected constructor that avoids that new instances of this utility class are accidentally created but still
     * allows this utility class to be inherited and enhanced.
     */
    protected Placeholders() {
    }
}
