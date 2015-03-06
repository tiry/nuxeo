package org.nuxeo.runtime.logging.logback;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.logging.LoggingConfigurator;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;

import ch.qos.logback.classic.BasicConfigurator;
import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.joran.spi.JoranException;

public class LogbackConfigurator implements LoggingConfigurator {

    static LogbackConfigurator self;

    public LogbackConfigurator() {
        self = this;
    }

    public class Justintime implements LoggingConfigurator.Justintime {

        final List<ILoggingEvent> store = new LinkedList<>();

        final Scope scope;

        final LoggerContext context;

        final LoggerContext sink;

        Justintime(Scope scope) {
            this.scope = scope;
            sink = LogbackJustintimeContextSelector.self.defaultContext;
            context = newLoggerContext();
        }

        @Override
        public void commit() {
            context.stop();
            for (ILoggingEvent event : store) {
                sink.getLogger(event.getLoggerName()).callAppenders(event);
            }
            handleStopped();
        }

        @Override
        public void forget(String message) {
            context.stop();
            sink.getLogger(LogbackConfigurator.class).info(message);
            handleStopped();
        }

        LoggerContext newLoggerContext() {
            LoggerContext context = new LoggerContext();
            context.setName(scope.getClass() + "@" + Integer.toHexString(scope.hashCode()));
            for (Logger logger:sink.getLoggerList()) {
                context.getLogger(logger.getName()).setLevel(logger.getEffectiveLevel());
            }
            Appender<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {

                @Override
                protected void append(ILoggingEvent event) {
                    store.add(event);
                }

            };
            appender.setContext(context);
            appender.start();
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
            return context;
        }

        void handleStarted() {
            context.start();
            scope.handleStart(this);
            justintimes.put(this, this);
        }

        void handleStopped() {
            store.clear();
            justintimes.remove(this);
            scope.handleStop(this);
        }

        @Override
        public <T> T adapt(Class<T> type) {
            if (type.isAssignableFrom(LoggerContext.class)) {
                if (!context.isStarted()) {
                    return type.cast(LogbackJustintimeContextSelector.self.defaultContext);
                }
                return type.cast(context);
            }
            throw new UnsupportedOperationException("Cannot adapt " + this + " onto " + type);
        }
    }

    final Map<Justintime, Justintime> justintimes = new ConcurrentHashMap<>();

    @Override
    public void justintime(Scope scope) {
        new Justintime(scope).handleStarted();
    }

    @Override
    public File getConfigurationFile(File directory) {
        return new File(directory, "logback.xml");
    }

    @Override
    public String[] getFileAppendersFiles(File file) {
        LoggerContext context = new LoggerContext();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        try {
            configurator.doConfigure(file.toURI().toURL());
        } catch (MalformedURLException | JoranException e) {
            throw new IllegalArgumentException("Cannot load config " + file);
        }
        List<String> files = new LinkedList<>();
        for (Logger logger : context.getLoggerList()) {
            Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders();
            while (it.hasNext()) {
                Appender<ILoggingEvent> appender = it.next();
                if (appender instanceof FileAppender) {
                    files.add(((FileAppender<ILoggingEvent>) appender).getFile());
                }
            }
        }
        return files.toArray(new String[files.size()]);
    }

    @Override
    public void initLogs(File logFile) {
        LoggerContext context = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
        if (context.isStarted()) {
            return;
        }
        if (logFile == null || !logFile.exists()) {
            context = (LoggerContext) LoggerFactory.getILoggerFactory();
            BasicConfigurator.configure(context);
            context.start();
        } else {
            context = (LoggerContext) LoggerFactory.getILoggerFactory();
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            try {
                configurator.doConfigure(logFile);
            } catch (JoranException e) {
                throw new IllegalArgumentException("Cannot load config " + logFile);

            }
        }
    }

    @Override
    public ILoggerFactory getFactory() {
        return LoggerFactory.getILoggerFactory();
    }

    @Override
    public void init() {
        Properties system = System.getProperties();
        String original = System.getProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
        synchronized (System.class) {
            system.setProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR,
                    LogbackJustintimeContextSelector.class.getName());
            try {
                Method reset = StaticLoggerBinder.class.getDeclaredMethod("reset");
                reset.setAccessible(true);
                reset.invoke(null);
                StaticLoggerBinder.getSingleton();
            } catch (ReflectiveOperationException cause) {
                LogFactory.getLog(LogbackJustintimeContextSelector.class).error("Cannot setup logback", cause);
            } finally {
                if (original != null) {
                    system.setProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR, original);
                } else {
                    system.remove(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
                }
            }
        }
    }

    @Override
    public void reset() {
        justintimes.keySet().forEach(jit -> jit.forget("logging reset"));
        StaticLoggerBinder binder = StaticLoggerBinder.getSingleton();
        try {
            Method method = binder.getClass().getDeclaredMethod("reset");
            method.setAccessible(true);
            method.invoke(binder);
            StaticLoggerBinder.getSingleton();
        } catch (ReflectiveOperationException cause) {
            LogFactory.getLog(LogbackJustintimeContextSelector.class).error("Cannot reset logback", cause);
        }
    }

    @Override
    public void setQuiet(String appenderName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDebug(String category, boolean debug) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDebug(String[] categories, boolean debug, boolean includeChildren, String[] appenderNames) {
        throw new UnsupportedOperationException();
    }

    LoggerContext selectContext() {
        return justintimes.keySet()
                .stream()
                .map(jit -> jit.scope.get())
                .filter(jit -> jit != null)
                .map(jit -> jit.adapt(LoggerContext.class))
                .findFirst()
                .orElse(null);
    }

}