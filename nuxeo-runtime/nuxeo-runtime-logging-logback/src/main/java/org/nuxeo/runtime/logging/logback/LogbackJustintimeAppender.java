package org.nuxeo.runtime.logging.logback;

import java.util.LinkedList;
import java.util.List;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class LogbackJustintimeAppender extends AppenderBase<ILoggingEvent> {

    final List<ILoggingEvent> store = new LinkedList<>();

    LogbackJustintimeAppender() {
        super();
    }

    @Override
    protected void append(ILoggingEvent event) {
        store.add(event);
    }

    Iterable<ILoggingEvent> store() {
        return store;
    }

    @Override
    public void start() {
        super.start();
        context.putObject("events", store);
    }

    @Override
    public void stop() {
        store.clear();
        super.stop();
    }

}
