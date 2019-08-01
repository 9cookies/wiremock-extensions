package com.ninecookies.wiremock.extensions.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Matcher;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ninecookies.wiremock.extensions.util.Placeholders.Keyword;

public class PlaceholdersKeywordTest {

    private static final long OFFSET_MILLIS = 600;

    @Test
    public void testUUIDPattern() {
        Matcher isKey = Placeholders.KEYWORD_PATTERN.matcher("$(!UUID)");
        assertTrue(isKey.matches());

        Keyword keyword = Keyword.of(isKey.group(1));
        assertNotNull(keyword);

        Object value = keyword.value(isKey.group(2));
        assertNotNull(value);

        UUID uuid = UUID.fromString(value.toString());
        assertNotNull(uuid);
    }

    @Test
    public void testRandomPattern() {
        Matcher isKey = Placeholders.KEYWORD_PATTERN.matcher("$(!Random)");
        assertTrue(isKey.matches());

        Keyword keyword = Keyword.of(isKey.group(1));
        assertNotNull(keyword);

        Object value = keyword.value(isKey.group(2));
        assertNotNull(value);

        Integer integer = Integer.valueOf(value.toString());
        assertNotNull(integer);
    }

    @Test(dataProvider = "offsetDateTimePatternsAndFixtures")
    public void testOffsetDateTimePatterns(String pattern, OffsetDateTime expected) {
        Matcher isKey = Placeholders.KEYWORD_PATTERN.matcher(pattern);
        assertTrue(isKey.matches());

        Keyword keyword = Keyword.of(isKey.group(1));
        assertNotNull(keyword);

        Object value = keyword.value(isKey.group(2));
        assertNotNull(value);

        assertThat(OffsetDateTime.parse(value.toString()))
                .isCloseTo(expected, within(OFFSET_MILLIS, ChronoUnit.MILLIS));
    }

    @DataProvider
    private Object[][] offsetDateTimePatternsAndFixtures() {
        return new Object[][] {
                { "$(!OffsetDateTime)", OffsetDateTime.now() },
                { "$(!OffsetDateTime.plus[s1])", OffsetDateTime.now().plusSeconds(1) },
                { "$(!OffsetDateTime.plus[s-1])", OffsetDateTime.now().plusSeconds(-1) },
                { "$(!OffsetDateTime.plus[m1])", OffsetDateTime.now().plusMinutes(1) },
                { "$(!OffsetDateTime.plus[m-1])", OffsetDateTime.now().plusMinutes(-1) },
                { "$(!OffsetDateTime.plus[h1])", OffsetDateTime.now().plusHours(1) },
                { "$(!OffsetDateTime.plus[h-1])", OffsetDateTime.now().plusHours(-1) },
        };
    }

    @Test(dataProvider = "instantPatternsAndFixtures")
    public void testInstantPatterns(String pattern, Instant expected) {
        Matcher isKey = Placeholders.KEYWORD_PATTERN.matcher(pattern);
        assertTrue(isKey.matches());

        Keyword keyword = Keyword.of(isKey.group(1));
        assertNotNull(keyword);

        Object value = keyword.value(isKey.group(2));
        assertNotNull(value);

        assertThat(Instant.parse(value.toString()))
                .isCloseTo(expected, within(OFFSET_MILLIS, ChronoUnit.MILLIS));
    }

    @DataProvider
    private Object[][] instantPatternsAndFixtures() {
        return new Object[][] {
                { "$(!Instant)", Instant.now() },
                { "$(!Instant.plus[s1])", Instant.now().plus(1, ChronoUnit.SECONDS) },
                { "$(!Instant.plus[s-1])", Instant.now().plus(-1, ChronoUnit.SECONDS) },
                { "$(!Instant.plus[m1])", Instant.now().plus(1, ChronoUnit.MINUTES) },
                { "$(!Instant.plus[m-1])", Instant.now().plus(-1, ChronoUnit.MINUTES) },
                { "$(!Instant.plus[h1])", Instant.now().plus(1, ChronoUnit.HOURS) },
                { "$(!Instant.plus[h-1])", Instant.now().plus(-1, ChronoUnit.HOURS) },
        };
    }

    @Test(dataProvider = "timestampPatternsAndFixtures")
    public void testTimestampPatterns(String pattern, long expected) {
        Matcher isKey = Placeholders.KEYWORD_PATTERN.matcher(pattern);
        assertTrue(isKey.matches());

        Keyword keyword = Keyword.of(isKey.group(1));
        assertNotNull(keyword);

        Object value = keyword.value(isKey.group(2));
        assertNotNull(value);

        assertThat(Long.parseLong(value.toString())).isCloseTo(expected, within(OFFSET_MILLIS));
    }

    @DataProvider
    private Object[][] timestampPatternsAndFixtures() {
        return new Object[][] {
                { "$(!Timestamp)", Instant.now().toEpochMilli() },
                { "$(!Timestamp.plus[s1])", Instant.now().plus(1, ChronoUnit.SECONDS).toEpochMilli() },
                { "$(!Timestamp.plus[s-1])", Instant.now().plus(-1, ChronoUnit.SECONDS).toEpochMilli() },
                { "$(!Timestamp.plus[m1])", Instant.now().plus(1, ChronoUnit.MINUTES).toEpochMilli() },
                { "$(!Timestamp.plus[m-1])", Instant.now().plus(-1, ChronoUnit.MINUTES).toEpochMilli() },
                { "$(!Timestamp.plus[h1])", Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli() },
                { "$(!Timestamp.plus[h-1])", Instant.now().plus(-1, ChronoUnit.HOURS).toEpochMilli() },
        };
    }

}
