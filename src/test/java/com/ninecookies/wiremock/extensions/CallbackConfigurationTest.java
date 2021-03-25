package com.ninecookies.wiremock.extensions;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import javax.jms.JMSException;

import org.testng.annotations.Test;

import com.ninecookies.wiremock.extensions.util.SystemUtil;

public class CallbackConfigurationTest {

    // explicit test of defined values with dedicated configuration instance to not interfere general callback tests
    @Test
    public void testSettings() throws JMSException, NoSuchMethodException, SecurityException, InstantiationException,
            IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        SystemUtil.setenv("SCHEDULED_THREAD_POOL_SIZE", "100");
        SystemUtil.setenv("RETRY_BACKOFF", "2500");
        SystemUtil.setenv("MAX_RETRIES", "3");
        SystemUtil.setenv("AWS_REGION", "");

        Constructor<CallbackConfiguration> ctor = CallbackConfiguration.class.getDeclaredConstructor();
        if (!ctor.isAccessible()) {
            ctor.setAccessible(true);
        }
        CallbackConfiguration config = ctor.newInstance();

        assertEquals(config.getCorePoolSize(), 100);
        assertEquals(config.getMaxRetries(), 3);
        assertEquals(config.getRetryBackoff(), 2_500);
        assertFalse(config.isMessagingEnabled());
        assertNull(config.createConnectionFactory());
        assertNull(config.createConnection());
        assertNull(config.createSnsClient());
        assertNull(config.createSqsClient());
    }
}
