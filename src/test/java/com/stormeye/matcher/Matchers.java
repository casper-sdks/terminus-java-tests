package com.stormeye.matcher;

import com.casper.sdk.model.event.Event;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Map of EventType to list of Matchers.
 *
 * @author ian@meywood.com
 */
public class Matchers {

    private final List<Matcher<?>> matchers = new ArrayList<>();

    public void addEventMatcher(final Matcher<?> matcher) {
        matchers.add(matcher);
    }


    private List<Matcher<?>> getMatchers() {
        return matchers;
    }


    public void handleEvent(final Event<?> event) {
        matchers.forEach(matcher -> matcher.matches(event));
    }

    public void removeEventMatcher(final Matcher<?> matcher) {
        matchers.remove(matcher);
    }
}
