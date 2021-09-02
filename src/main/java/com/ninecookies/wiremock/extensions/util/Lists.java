package com.ninecookies.wiremock.extensions.util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Provides methods to construct {@link List}s.
 *
 * @author M.Scheepers
 * @since 0.3.1
 */
public class Lists {

    /**
     * Creates a list with value type {@code <V>} and populates it with the specified <i>elements</i>.
     *
     * @param <V> the value type.
     * @param elements the elements of defined value type.
     * @return a new unmodifiable {@link List} populated with the specified <i>elements</i>.
     */
    @SafeVarargs
    public static <V> List<V> listOf(V... elements) {
        List<V> result = new LinkedList<>();
        for (V element : elements) {
            result.add(element);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Protected constructor that avoids that new instances of this utility class are accidentally created but still
     * allows this utility class to be inherited and enhanced.
     */
    protected Lists() {
    }
}
