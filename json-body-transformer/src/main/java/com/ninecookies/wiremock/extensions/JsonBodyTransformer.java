package com.ninecookies.wiremock.extensions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.Response;

public class JsonBodyTransformer extends ResponseTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(JsonBodyTransformer.class);

    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private static final Set<RequestMethod> METHODS_WITH_CONTENT = new HashSet<>(
            Arrays.asList(RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH));

    @Override
    public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
        if (!requiresTransformation(response)) {
            LOG.info("transform('{}', '{}')", request, Objects.describe(response));
            return response;
        }

        String responseBody = response.getBodyAsString();
        String requestBody = extractJsonBody(request);

        Map<String, Object> responsePlaceholders = Placeholders.parseJsonBody(responseBody);
        Placeholders.parsePlaceholderValues(responsePlaceholders, requestBody);
        String transformedResponseBody = Placeholders.replaceValuesInJson(responsePlaceholders, responseBody);
        Response result = Response.Builder.like(response).but().body(transformedResponseBody).build();
        LOG.info("transform('{}') -> '{}'", request, Objects.describe(result));
        return result;
    }

    @Override
    public String getName() {
        return "json-body-transformer";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    private boolean requiresTransformation(Response response) {
        // nothing to do for an empty body
        if (response.getBody() == null || response.getBody().length == 0) {
            LOG.info("skip transformation of empty response");
            return false;
        }
        // nothing to do for response content type other than application/json
        if (!response.getHeaders().getContentTypeHeader().isPresent()
                || !CONTENT_TYPE_APPLICATION_JSON.equals(response.getHeaders().getContentTypeHeader().mimeTypePart())) {
            LOG.info("skip transformation of unknown response (headers: '{}')",
                    response.getHeaders().toString().trim());
            return false;
        }
        return true;
    }

    private String extractJsonBody(Request request) {
        String result = null;
        if (METHODS_WITH_CONTENT.contains(request.getMethod())) {
            if (!request.contentTypeHeader().isPresent()
                    || !CONTENT_TYPE_APPLICATION_JSON.equals(request.contentTypeHeader().mimeTypePart())) {
                LOG.debug("skip request parsing due to content type '{}'", request.contentTypeHeader());
            } else {
                result = request.getBodyAsString();
            }
        } else {
            LOG.debug("skip request parsing due to method '{}'", request.getMethod());
        }
        return result;
    }
}
