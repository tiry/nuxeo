package org.nuxeo.osgi.system;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.osgi.service.log.LogService;

public class OSGiThreadFactoryBuilder {

    private class OSGiThreadFactory implements ThreadFactory {
        AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = backingThreadFactory.newThread(runnable);
            thread.setName(String.format("%s-%s-%02d", system.environment.name,
                    thread.getThreadGroup().getName(), counter.getAndIncrement()));
            if (priority != null) {
                thread.setPriority(priority);
            }
            thread.setContextClassLoader(system.bootstrap.loader());
            thread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            return thread;
        }
    }

    private class OSGiBackingThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group == null ? system.adapt(ThreadGroup.class)
                    : group, r);
        }
    }

    private class OSGiUncaughtExceptionHandler implements UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            system.adapt(LogService.class).log(LogService.LOG_ERROR, "uncaught error on thread " + t, e);
        }
    }

    final OSGiSystem system;

    public OSGiThreadFactoryBuilder(OSGiSystem system) {
        this.system = system;
    }

    OSGiThreadPoolBuilder poolBuilder;

    OSGiThreadFactoryBuilder withPoolBuilder(OSGiThreadPoolBuilder instance) {
        poolBuilder = instance;
        return this;
    }

    public OSGiThreadFactoryBuilder withDaemon(Boolean daemon) {
        group.setDaemon(true);
        return this;
    }

    protected Integer priority = null;

    public OSGiThreadFactoryBuilder withPriority(int priority) {
        this.priority = priority;
        return this;
    }

    protected UncaughtExceptionHandler uncaughtExceptionHandler = new OSGiUncaughtExceptionHandler();

    public OSGiThreadFactoryBuilder withHandler(UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
        return this;
    }

    protected ThreadFactory backingThreadFactory = new OSGiBackingThreadFactory();

    public OSGiThreadFactoryBuilder withBackingFactory(ThreadFactory backingThreadFactory) {
        this.backingThreadFactory = backingThreadFactory;
        return this;
    }

    ThreadGroup group;

    public OSGiThreadFactoryBuilder withGroup(String name) {
        group = new ThreadGroup(system.adapt(ThreadGroup.class), name);
        return this;
    }

    public OSGiThreadFactoryBuilder withGroup(ThreadGroup group) {
        this.group = group;
        return this;
    }

    public ThreadFactory build() {
        return new OSGiThreadFactory();
    }

    public OSGiThreadPoolBuilder end() {
        return poolBuilder.withThreadFactory(build(), group);
    }

}
