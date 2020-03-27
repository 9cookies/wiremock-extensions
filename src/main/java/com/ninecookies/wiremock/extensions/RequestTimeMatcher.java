package com.ninecookies.wiremock.extensions;

import java.time.Instant;
import java.util.regex.Pattern;

import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.RequestMatcherExtension;
import com.ninecookies.wiremock.extensions.util.Strings;

/**
 * Extends the {@link RequestMatcherExtension} and provides the ability to match the UTC request time against a provided
 * regular expression.
 *
 * @author M.Scheepers
 * @since 0.0.7
 */
public class RequestTimeMatcher extends RequestMatcherExtension {

    @Override
    public String getName() {
        return "request-time-matcher";
    }

    @Override
    public MatchResult match(Request request, Parameters parameters) {
        String pattern = parameters.getString("pattern");
        if (Strings.isNullOrEmpty(pattern)) {
            return MatchResult.of(false);
        }
        return MatchResult.of((Pattern.matches(pattern, Instant.now().toString())));
    }
}
