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
 * Provides convenient methods to parse, populate and replace placeholders in template strings.
 *
 * @author M.Scheepers
 * @since 0.0.6
 */
public class Placeholders {
    private static final Logger LOG = LoggerFactory.getLogger(Placeholders.class);
    private static final UnaryOperator<String> QUOTES = s -> String.format("\"%s\"", s);
    private static final ConfigurationBuilder JSON_CONTEXT_CONFIGURATION_BUILDER = Configuration.builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
            .options(Option.SUPPRESS_EXCEPTIONS);

    /**
     * Matches placeholders and keywords '\$\(.*?\)'
     */
    static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\(.*?\\)");

    // visible for testing
    /**
     * Matches keywords '\$\(!(Random|OffsetDateTime|Instant|ENV|UUID|Timestamp)(.*)\)'
     * group 1 -> keyword
     * group 2 -> parameters
     */
    static final Pattern KEYWORD_PATTERN = Pattern.compile("\\$\\(!(" +
            Stream.of(Keyword.keywords()).map(Keyword::keyword).collect(Collectors.joining("|"))
            + ")(.*)\\)");

    /**
     * Creates a {@link DocumentContext} for the specified <i>json</i> string.
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
     * Replaces all placeholders in the specified <i>templateJson</i> with the related values looked up in the
     * specified <i>sourceJson</i>.
     *
     * @param sourceJson the source JSON string to look up placeholder values.
     * @param templateJson the template JSON string containing the placeholders.
     * @return the JSON result of the template with placeholders replaced by their related values.
     */
    public static String transformJson(String sourceJson, String templateJson) {
        return transformJson(documentContextOf(sourceJson), templateJson);
    }

    /**
     * Replaces all placeholders in the specified <i>templateJson</i> with the related values looked up in the
     * specified <i>sourceContext</i>.
     *
     * @param sourceContext the source {@link DocumentContext} to look up placeholder values.
     * @param templateJson the template JSON string containing the placeholders.
     * @return the JSON result of the template with placeholders replaced by their related values.
     */
    public static String transformJson(DocumentContext sourceContext, String templateJson) {
        Map<String, Object> placeholders = parsePlaceholders(templateJson, sourceContext);
        return transformJson(placeholders, templateJson);
    }

    /**
     * Replaces all specified <i>placeholders</i> in the specified <i>templateJson</i> with their defined values.
     *
     * @param placeholders a {@link Map} with placeholder patterns and replacement values.
     * @param templateJson the template JSON string containing the placeholders.
     * @return the JSON result of the template with placeholders replaced by their related values.
     */
    public static String transformJson(Map<String, Object> placeholders, String templateJson) {
        String result = templateJson;
        for (Entry<String, Object> placeholder : placeholders.entrySet()) {
            String pattern = placeholder.getKey();
            String value = String.valueOf(placeholder.getValue());
            String quotedPattern = QUOTES.apply(pattern);
            String jsonValue = Json.write(placeholder.getValue());
            // first replace all occurrences of "$(property.path)" with it's JSON value
            // and then check for in string replacements like "arbitrary text with $(embedded) placeholder"
            result = result.replace(quotedPattern, jsonValue).replace(pattern, value);
        }
        LOG.debug("transformJson('{}', '{}') -> '{}'", placeholders, templateJson, result);
        return result;
    }

    /**
     * Checks whether the specified <i>value</i> is a keyword pattern and if so returns the keyword generated value.
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
     * Replaces placeholders and keywords in the specified {@code value} by values found in the specified
     * {@code placeholderSource}.
     *
     * @param sourceContext the source {@link DocumentContext} to look up placeholder values.
     * @param value
     * @return
     */
    public static String transformValue(DocumentContext sourceContext, String value) {
        Map<String, Object> placeholders = parsePlaceholders(value, sourceContext);
        return transformValue(placeholders, value, false);
    }

    public static String transformValue(DocumentContext sourceContext, String value, boolean isUrl) {
        Map<String, Object> placeholders = parsePlaceholders(value, sourceContext);
        return transformValue(placeholders, value, isUrl);
    }

    /**
     * Replaces placeholders and keywords in the specified {@code value} by values found in the specified
     * {@code placeholderSource}. If {@code isUrl} is {@code true} the value is treated as URL and query string
     * replacement values will be URL encoded.
     *
     * @param placeholders a {@link Map} with placeholder patterns and their replacement values.
     * @param value the value to transform
     * @param isUrl indicates whether value is an URL.
     * @return the transformed value.
     */
    public static String transformValue(Map<String, Object> placeholders, String value, boolean isUrl) {
        for (String key : placeholders.keySet()) {
            int keyStart = value.indexOf(key);
            if (keyStart == -1) {
                continue;
            }
            int queryStringStart = (isUrl) ? value.indexOf('?') : -1;
            String replacement = String.valueOf(placeholders.get(key));
            if (queryStringStart > 0 && keyStart > queryStringStart) {
                replacement = urlEncodeValue(replacement);
            }
            value = value.replace(key, replacement);
        }
        return value;
    }

    /**
     * Parses the specified string {@code expression} for placeholder patterns.<br>
     * <b>Note</b>: placeholders for keywords will get their values immediately even when the optional
     * {@code sourceContext} is omitted.
     *
     * @param expression the JSON {@link String} that may contain placeholders.
     * @param sourceContext the source {@link DocumentContext} to look up placeholder values.
     * @return a {@link Map} containing entries for all found placeholders.
     */
    public static Map<String, Object> parsePlaceholders(String expression, DocumentContext sourceContext) {
        Map<String, Object> result = new LinkedHashMap<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(expression);
        while (matcher.find()) {
            String placeholder = matcher.group();
            if (result.containsKey(placeholder)) {
                continue;
            }
            result.put(placeholder, populatePlaceholder(placeholder, sourceContext));
        }
        LOG.debug("parsePlaceholders('{}') -> '{}'", expression, result);
        return result;
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

    private static String urlEncodeValue(String value) {
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
