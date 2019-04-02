package com.ninecookies.wiremock.extensions;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.jayway.jsonpath.DocumentContext;
import com.ninecookies.wiremock.extensions.api.Callback;
import com.ninecookies.wiremock.extensions.api.Callbacks;

public class CallbackSimulator extends PostServeAction {

    public static class PostTask extends TimerTask {

        private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
                .setSocketTimeout(2_000)
                .setConnectTimeout(3_000)
                .setConnectionRequestTimeout(5_000)
                .build();

        private String uri;
        private HttpEntity content;

        @Override
        public void run() {
            HttpPost post = new HttpPost(uri);
            post.setConfig(REQUEST_CONFIG);
            post.setEntity(content);
            try (CloseableHttpClient client = HttpClientBuilder
                    .create().build()) {
                HttpResponse response = client.execute(post);
                LOG.info("post response: {}", response);
            } catch (Exception e) {
                LOG.error("POST request to '{}' with '{}' failed", uri, content, e);
            }
        }

        public static PostTask of(String uri, Object content) {
            PostTask result = new PostTask();
            result.uri = uri;
            result.content = new ByteArrayEntity(Json.toByteArray(content), ContentType.APPLICATION_JSON);
            return result;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(CallbackSimulator.class);
    private static long instances = 0;
    private final long instance = ++instances;
    private final Timer timer = new Timer("callback-timer", true);

    @Override
    public String getName() {
        return "callback-simulator";
    }

    @Override
    public void doAction(ServeEvent serveEvent, Admin admin, Parameters parameters) {
        LOG.debug("doAction[{}](serveEvent: {}, admin: {}, parameters: {})", instance, serveEvent, admin, parameters);

        // compose JSON path parsable request/response json
        DocumentContext servedJson = Placeholders.documentContextOf("{\"request\":"
                + serveEvent.getRequest().getBodyAsString() + ", \"response\":"
                + serveEvent.getResponse().getBodyAsString() + "}");

        Callbacks callbacks = parameters.as(Callbacks.class);

        for (Callback callback : callbacks.callbacks) {
            LOG.debug("callback.data: {}", Objects.describe(callback.data));
            // should always be, but...
            if (!Map.class.isAssignableFrom(callback.data.getClass())) {
                LOG.error("callback.data is not of type Map: '{}'",
                        callback.data == null ? null : callback.data.getClass());
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> data = (Map<Object, Object>) callback.data;
            for (Entry<Object, Object> entry : data.entrySet()) {
                LOG.debug("entry: {} -> {}", Objects.describe(entry.getKey()), Objects.describe(entry.getValue()));
                if (entry.getValue() instanceof String) {
                    String value = (String) entry.getValue();
                    if (Placeholders.isPlaceholder(value)) {
                        entry.setValue(Placeholders.populatePlaceholder(value, servedJson));
                    }
                }
            }
            Object url = Placeholders.isPlaceholder(callback.url)
                    ? Placeholders.populatePlaceholder(callback.url, servedJson)
                    : callback.url;
            LOG.info("scheduling callback task to: '{}' with delay '{}' and data '{}'", url, callback.delay, data);
            timer.schedule(PostTask.of(url.toString(), data), callback.delay);
        }
    }
}
