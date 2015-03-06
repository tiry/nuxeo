package org.nuxeo.osgi.system;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

public class OSGiThreadPoolBuilder {

    final OSGiSystem system;

    public OSGiThreadPoolBuilder(OSGiSystem system) {
        this.system = system;
    }

    public OSGiThreadFactoryBuilder onFactory() {
        return new OSGiThreadFactoryBuilder(system).withPoolBuilder(this);
    }

    ThreadFactory threadFactory = new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r);
        }
    };

    ThreadGroup threadGroup = null;

    OSGiThreadPoolBuilder withThreadFactory(ThreadFactory factory, ThreadGroup group) {
        threadFactory = factory;
        threadGroup = group;
        return this;
    }

    boolean inlined = false;

    public OSGiThreadPoolBuilder inline() {
        inlined = true;
        return this;
    }

    int threadCount;

    public OSGiThreadPoolBuilder withCached() {
        threadCount = 0;
        return this;
    }

    public OSGiThreadPoolBuilder withFixedThreadCount(int count) {
        threadCount = count;
        return this;
    }

    public OSGiThreadPoolBuilder withSingleThread() {
        threadCount = 1;
        return this;
    }

    public ThreadPoolExecutor build() {
        ThreadPoolExecutor executor = threadCount == 0
                ? (ThreadPoolExecutor) Executors.newCachedThreadPool(threadFactory)
                : (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount, threadFactory);
        return executor;
    }
}
