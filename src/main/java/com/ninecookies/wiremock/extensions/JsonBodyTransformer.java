package com.ninecookies.wiremock.extensions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.Response;
import com.jayway.jsonpath.DocumentContext;
import com.ninecookies.wiremock.extensions.util.Placeholders;

public class JsonBodyTransformer extends ResponseTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(JsonBodyTransformer.class);

    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private static final Set<RequestMethod> METHODS_WITH_CONTENT = new HashSet<>(
            Arrays.asList(RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH));

    @Override
    public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
        LOG.info("transform('{}', '{}')", request.getMethod(), request.getAbsoluteUrl());
        if (!requiresTransformation(response)) {
            return response;
        }
        String responseBody = response.getBodyAsString();
        DocumentContext placeholderSource = preparePlaceholderSource(request);
        String transformedResponseBody = Placeholders.transformJson(placeholderSource, responseBody);
        Response result = Response.Builder.like(response).but().body(transformedResponseBody).build();
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
            LOG.debug("skip transformation of empty response");
            return false;
        }
        // nothing to do for response content type other than application/json
        if (!response.getHeaders().getContentTypeHeader().isPresent()
                || !CONTENT_TYPE_APPLICATION_JSON.equals(response.getHeaders().getContentTypeHeader().mimeTypePart())) {
            LOG.debug("skip transformation of unknown response (headers: '{}')",
                    response.getHeaders().toString().trim());
            return false;
        }
        return true;
    }

    private DocumentContext preparePlaceholderSource(Request request) {
        String json = "{}";
        if (METHODS_WITH_CONTENT.contains(request.getMethod())) {
            if (!request.contentTypeHeader().isPresent()
                    || !CONTENT_TYPE_APPLICATION_JSON.equals(request.contentTypeHeader().mimeTypePart())) {
                LOG.debug("skip request parsing due to content type '{}'", request.contentTypeHeader());
            } else {
                json = request.getBodyAsString();
            }
        } else {
            LOG.debug("skip request parsing due to method '{}'", request.getMethod());
        }

        List<String> urlParts = Placeholders.splitUrl(request.getUrl());
        DocumentContext result = Placeholders.documentContextOf(json);
        return result.put("$", "urlParts", urlParts);
    }
}
