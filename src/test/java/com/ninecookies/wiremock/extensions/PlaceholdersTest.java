package com.ninecookies.wiremock.extensions;

import org.testng.annotations.Test;

import com.ninecookies.wiremock.extensions.util.Placeholders;

public class PlaceholdersTest {

    @Test
    public void test() {
        System.out.println(Placeholders.populatePlaceholder("$(!Instant.plus[m10].pickup)"));
    }
}
