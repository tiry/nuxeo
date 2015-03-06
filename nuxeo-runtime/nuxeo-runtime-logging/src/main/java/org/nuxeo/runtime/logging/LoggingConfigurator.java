package org.nuxeo.runtime.logging;

import java.io.File;
import java.util.ServiceLoader;

import org.osgi.framework.BundleReference;
import org.slf4j.ILoggerFactory;

public interface LoggingConfigurator {

    void init();

    void reset();

    File getConfigurationFile(File directory);

    String[] getFileAppendersFiles(File log4jConfFile);

    void initLogs(File logFile);

    ILoggerFactory getFactory();

    void setQuiet(String appenderName);

    void setDebug(String category, boolean debug);

    void setDebug(String[] categories, boolean debug, boolean includeChildren, String[] appenderNames);

    public interface Justintime {
        void commit();

        void forget(String message);

        <T> T adapt(Class<T> type);
    }

    public abstract class Scope {

        public abstract void handleStart(Justintime activation);

        public abstract void handleStop(Justintime activation);

        public abstract Justintime get();

        public static final Scope APPLICATION_SCOPE = new Scope() {
            Justintime activation;

            @Override
            public void handleStart(Justintime activation) {
                this.activation = activation;
            }

            @Override
            public void handleStop(Justintime activation) {
                this.activation = null;
            }

            @Override
            public Justintime get() {
                return activation;
            }
        };

        public static final Scope THREAD_SCOPE = new Scope() {

            ThreadLocal<Justintime> holder = new ThreadLocal<>();

            @Override
            public void handleStart(Justintime activation) {
                holder.set(activation);
            }

            @Override
            public void handleStop(Justintime activation) {
                holder.remove();
            }

            @Override
            public Justintime get() {
                return holder.get();
            }
        };

    }

    void justintime(Scope scope);

    static LoggingConfigurator getInstance() {
        return Holder.SELF;
    }

    public static class Holder {
        public static final LoggingConfigurator SELF = loadConfigurator();

        public static LoggingConfigurator loadConfigurator() {
            if (LoggingConfigurator.class.getClassLoader() instanceof BundleReference) {
                return LoggingActivator.self.configurator;
            }
            LoggingConfigurator configurator = ServiceLoader.load(LoggingConfigurator.class).iterator().next();
            configurator.init();
            return configurator;
        }
    }

}
