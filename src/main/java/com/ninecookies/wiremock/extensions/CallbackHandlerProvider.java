package com.ninecookies.wiremock.extensions;

import java.util.Map;

import com.ninecookies.wiremock.extensions.api.Callback;

/**
 * Defines the methods a callback handler provider must implement to be usable by the {@link CallbackSimulator}.
 *
 * @author M.Scheepers
 * @since 0.3.1
 */
public interface CallbackHandlerProvider {

    /**
     * Indicates whether this callback handler provider is able to handle the specified {@link Callback}.
     *
     * @param callback the public API {@link Callback} model information.
     * @return {@code true} if the handling of the callback is supported; otherwise {@code false}.
     */
    boolean supports(Callback callback);

    /**
     * Creates a new instance of a callback handler {@link Runnable} according to the required callback channel.
     *
     * @param callback the public API {@link Callback} model information.
     * @param placeholders the context for placeholder substitution.
     * @return the callback handler implementation according to the {@link Callback} or {@code null} if the callback
     *         target (URL, queue, topic) resolution contains a placeholder or keyword that resolved to {@code "null"}.
     */
    Runnable get(Callback callback, Map<String, Object> placeholders);
}
