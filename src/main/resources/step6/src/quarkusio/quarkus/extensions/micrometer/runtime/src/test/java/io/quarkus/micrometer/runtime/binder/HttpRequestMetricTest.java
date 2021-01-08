package io.quarkus.micrometer.runtime.binder; // (rank 94) copied from https://github.com/quarkusio/quarkus/blob/3a26a14b9121b8d8348e4e68634428e2216d5f21/extensions/micrometer/runtime/src/test/java/io/quarkus/micrometer/runtime/binder/HttpRequestMetricTest.java

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.mockito.Mockito;

import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

/**
 * Disabled on Java 8 because of Mocks
 */
@DisabledOnJre(JRE.JAVA_8)
public class HttpRequestMetricTest {

    final List<Pattern> NO_IGNORE_PATTERNS = Collections.emptyList();
    final List<Pattern> ignorePatterns = Arrays.asList(Pattern.compile("/ignore.*"));

    final Map<Pattern, String> NO_MATCH_PATTERNS = Collections.emptyMap();

    @Test
    public void testReturnPathFromHttpRequestPath() {
        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.routingContext = Mockito.mock(RoutingContext.class);

        Mockito.when(requestMetric.routingContext.get(HttpRequestMetric.HTTP_REQUEST_PATH))
                .thenReturn("/item/{id}");

        Assertions.assertEquals("/item/{id}", requestMetric.getHttpRequestPath());
    }

    @Test
    public void testReturnPathFromRoutingContext() {
        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.routingContext = Mockito.mock(RoutingContext.class);
        Route currentRoute = Mockito.mock(Route.class);

        Mockito.when(requestMetric.routingContext.currentRoute()).thenReturn(currentRoute);
        Mockito.when(currentRoute.getPath()).thenReturn("/item");

        Assertions.assertEquals("/item", requestMetric.getHttpRequestPath());
    }

    @Test
    public void testReturnGenericPathFromRoutingContext() {
        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.routingContext = Mockito.mock(RoutingContext.class);
        Route currentRoute = Mockito.mock(Route.class);

        Mockito.when(requestMetric.routingContext.currentRoute()).thenReturn(currentRoute);
        Mockito.when(currentRoute.getPath()).thenReturn("/item/:id");

        Assertions.assertEquals("/item/{id}", requestMetric.getHttpRequestPath());
        // Make sure conversion is cached
        Assertions.assertEquals("/item/{id}", HttpRequestMetric.templatePath.get("/item/:id"));
    }

    @Test
    public void testParsePathDoubleSlash() {
        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.parseUriPath(NO_MATCH_PATTERNS, NO_IGNORE_PATTERNS, "//");
        Assertions.assertEquals("/", requestMetric.path);
        Assertions.assertTrue(requestMetric.measure);
        Assertions.assertFalse(requestMetric.pathMatched);
    }

    @Test
    public void testParseEmptyPath() {
        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.parseUriPath(NO_MATCH_PATTERNS, NO_IGNORE_PATTERNS, "");
        Assertions.assertTrue(requestMetric.measure);
        Assertions.assertFalse(requestMetric.pathMatched);
        Assertions.assertEquals("/", requestMetric.path);
    }

    @Test
    public void testParsePathNoLeadingSlash() {
        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.parseUriPath(NO_MATCH_PATTERNS, NO_IGNORE_PATTERNS, "path/with/no/leading/slash");
        Assertions.assertEquals("/path/with/no/leading/slash", requestMetric.path);
        Assertions.assertTrue(requestMetric.measure);
        Assertions.assertFalse(requestMetric.pathMatched);
    }

    @Test
    public void testParsePathWithQueryString() {
        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.parseUriPath(NO_MATCH_PATTERNS, NO_IGNORE_PATTERNS, "/path/with/query/string?stuff");
        Assertions.assertEquals("/path/with/query/string", requestMetric.path);
        Assertions.assertTrue(requestMetric.measure);
        Assertions.assertFalse(requestMetric.pathMatched);
    }

    @Test
    public void testParsePathIgnoreNoLeadingSlash() {
        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.parseUriPath(NO_MATCH_PATTERNS, ignorePatterns, "ignore/me/with/no/leading/slash");
        Assertions.assertEquals("/ignore/me/with/no/leading/slash", requestMetric.path);
        Assertions.assertFalse(requestMetric.measure);
        Assertions.assertFalse(requestMetric.pathMatched);
    }

    @Test
    public void testParsePathIgnoreWithQueryString() {
        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.parseUriPath(NO_MATCH_PATTERNS, ignorePatterns, "/ignore/me/with/query/string?stuff");
        Assertions.assertEquals("/ignore/me/with/query/string", requestMetric.path);
        Assertions.assertFalse(requestMetric.measure);
        Assertions.assertFalse(requestMetric.pathMatched);
    }

    @Test
    public void testParsePathMatchReplaceNoLeadingSlash() {
        final Map<Pattern, String> matchPatterns = new HashMap<>();
        matchPatterns.put(Pattern.compile("/item/\\d+"), "/item/{id}");

        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.parseUriPath(matchPatterns, NO_IGNORE_PATTERNS, "item/123");
        Assertions.assertEquals("/item/{id}", requestMetric.path);
        Assertions.assertTrue(requestMetric.measure);
        Assertions.assertTrue(requestMetric.pathMatched);
    }

    @Test
    public void testParsePathMatchReplaceLeadingSlash() {
        final Map<Pattern, String> matchPatterns = new HashMap<>();
        matchPatterns.put(Pattern.compile("/item/\\d+"), "/item/{id}");

        HttpRequestMetric requestMetric = new HttpRequestMetric();
        requestMetric.parseUriPath(matchPatterns, NO_IGNORE_PATTERNS, "/item/123");
        Assertions.assertEquals("/item/{id}", requestMetric.path);
        Assertions.assertTrue(requestMetric.measure);
        Assertions.assertTrue(requestMetric.pathMatched);
    }
}
