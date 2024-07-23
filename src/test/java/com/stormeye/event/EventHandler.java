package com.stormeye.event;

import com.casper.sdk.model.event.Event;
import com.casper.sdk.model.event.EventTarget;
import com.stormeye.matcher.Matchers;
import com.stormeye.utils.CasperClientProvider;
import org.hamcrest.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for consuming and matching events.
 *
 * @author ian@meywood.com
 */
public class EventHandler {

    private final Logger logger = LoggerFactory.getLogger(EventHandler.class);
    private final Matchers matcherMap = new Matchers();
    private final List<AutoCloseable> sseSources = new ArrayList<>();

    public EventHandler(final EventTarget eventTarget) {
        consume(eventTarget);
    }

    public void close() {

        for (AutoCloseable sseSource : sseSources) {
            try {
                sseSource.close();
            } catch (Exception e) {
                logger.error("Error closing SSE Source", e);
            }
        }
    }

    private void consume(final EventTarget eventTarget) {

        logger.info("Got event {}", eventTarget);
        sseSources.add(
                CasperClientProvider.getInstance().getEventService().consumeEvents(
                        eventTarget, null,
                        this::handleMatchers,
                        throwable -> logger.error("Error processing SSE event", throwable)
                )
        );
    }

    public <T> Matcher<T> addEventMatcher(final Matcher<T> matcher) {
        matcherMap.addEventMatcher(matcher);
        return matcher;
    }

    private void handleMatchers(Event<?> event) {
        matcherMap.handleEvent(event);
    }

    public <T> void removeEventMatcher(final Matcher<T> matcher) {
        matcherMap.removeEventMatcher(matcher);
    }
}
