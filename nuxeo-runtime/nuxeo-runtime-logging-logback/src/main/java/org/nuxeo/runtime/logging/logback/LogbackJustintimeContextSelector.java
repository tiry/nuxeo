package org.nuxeo.runtime.logging.logback;

import java.util.Collections;
import java.util.List;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.selector.ContextSelector;

public class LogbackJustintimeContextSelector implements ContextSelector {

    static LogbackJustintimeContextSelector self;

    final LoggerContext defaultContext;

    public LogbackJustintimeContextSelector(LoggerContext defaultContext) {
        this.defaultContext = defaultContext;
        self = this;
    }

    @Override
    public LoggerContext getLoggerContext() {
        LoggerContext context = LogbackConfigurator.self.selectContext();
        if (context != null) {
            return context;
        }
        return getDefaultLoggerContext();
    }

    @Override
    public LoggerContext getLoggerContext(String name) {
        return null;
    }

    @Override
    public LoggerContext getDefaultLoggerContext() {
        return defaultContext;
    }

    @Override
    public LoggerContext detachLoggerContext(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getContextNames() {
        return Collections.emptyList();
    }

}
