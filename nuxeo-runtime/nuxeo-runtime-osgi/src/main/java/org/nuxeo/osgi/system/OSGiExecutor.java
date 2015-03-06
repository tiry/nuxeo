package org.nuxeo.osgi.system;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.nuxeo.osgi.bootstrap.OSGiCaller;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

public class OSGiExecutor {

    class InlinedExecutor extends AbstractExecutorService {

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTerminated() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isShutdown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException();
        }
    }

    public interface Hook {

        <T> T onNewProxy(T proxied);

        <T> void onThreadEntered(T proxied);

        <T> void onThreadLeave(T proxied);

    };

    final ExecutorService inlined = new InlinedExecutor();

    final OSGiSystem system;

    ThreadGroup group = Thread.currentThread().getThreadGroup();

    ExecutorService executor = inlined;

    ExecutorService executor(Bundle bundle) {
        return inlined;
    }

    OSGiExecutor(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new OSGiBundleAdapter.BundleAdapter<OSGiExecutor>() {

            @Override
            public Class<OSGiExecutor> typeof() {
                return OSGiExecutor.class;
            }

            @Override
            public OSGiExecutor adapt(Bundle bundle) throws BundleException {
                return OSGiExecutor.this;
            }
        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new OSGiBundleAdapter.BundleAdapter<ThreadGroup>() {

            @Override
            public Class<ThreadGroup> typeof() {
                return ThreadGroup.class;
            }

            @Override
            public ThreadGroup adapt(Bundle bundle) throws BundleException {
                return group;
            }

        });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new OSGiBundleAdapter.BundleAdapter<ExecutorService>() {

                    @Override
                    public Class<ExecutorService> typeof() {
                        return ExecutorService.class;
                    }

                    @Override
                    public ExecutorService adapt(Bundle bundle) {
                        return executor(bundle);
                    }
                });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new OSGiBundleAdapter.BundleAdapter<OSGiCaller>() {

            @Override
            public Class<OSGiCaller> typeof() {
                return OSGiCaller.class;
            }

            @Override
            public OSGiCaller adapt(Bundle bundle) {
                return new OSGiCaller() {
                    @Override
                    public <T> T call(Callable<T> invoke) throws BundleException {
                        try {
                            return executor(bundle).submit(new java.util.concurrent.Callable<T>() {

                                @Override
                                public T call() throws Exception {
                                    ClassLoader tcl = Thread.currentThread().getContextClassLoader();
                                    if ((bundle.getState() & Bundle.RESOLVED) == 0) {
                                        Thread.currentThread().setContextClassLoader(system.bootstrap.loader());
                                    } else {
                                        Thread.currentThread().setContextClassLoader(bundle.adapt(ClassLoader.class));
                                    }
                                    try {
                                        return invoke.call(bundle);
                                    } finally {
                                        Thread.currentThread()
                                                .setContextClassLoader(tcl);
                                    }
                                }

                            }).get();
                        } catch (InterruptedException cause) {
                            Thread.currentThread().interrupt();
                            throw new BundleException(
                                    "Interrupted thread " + Thread.currentThread() + " while invoking " + invoke,
                                    cause);
                        } catch (ExecutionException cause) {
                            throw unwrap(cause)
                                    .orElseGet(() -> new BundleException("Caught while invoking " + invoke,
                                            cause.getCause()));
                        }
                    }

                    @Override
                    public void run(Runnable runnable) throws BundleException {
                        call(new Callable<Void>() {

                            @Override
                            public Void call(Bundle bundle) throws BundleException {
                                runnable.run(bundle);
                                return null;
                            }

                        });
                    }
                };

            }

        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new OSGiBundleAdapter.BundleAdapter<Adapter>() {

            @Override
            public Class<Adapter> typeof() {
                return Adapter.class;
            }

            @Override
            public Adapter adapt(Bundle bundle) {
                return new Adapter(bundle);
            }

        });
    }

    ExecutorService selectExecutor(ThreadGroup group, ExecutorService service) {
        return service;
    }

    public class Adapter {
        final Bundle bundle;

        Adapter(Bundle bundle) {
            this.bundle = bundle;
        }

        public <T> T adapt(Class<T> type, T instance) {
            T proxy = type
                    .cast(Proxy.newProxyInstance(
                            type.getClassLoader(),
                            new Class[] { type }, new InvocationHandler() {

                                @Override
                                public Object invoke(Object proxy, final Method method, final Object[] args)
                                        throws Throwable {
                                    return bundle.adapt(OSGiCaller.class).call(new OSGiCaller.Callable<Object>() {

                                        @Override
                                        public Object call(Bundle bundle) throws BundleException {
                                            try {
                                                return method.invoke(instance, args);
                                            } catch (ReflectiveOperationException cause) {
                                                throw unwrap(cause).orElseGet(() -> new BundleException(
                                                        "Caught error while invoking " + method + " on " + instance,
                                                        cause.getCause()));
                                            }
                                        }

                                    });
                                }
                            }));
            return proxy;
        }

    }

    <T> T adapt(Class<T> type) {
        return type.cast(new BundleActivator() {

            @Override
            public void start(BundleContext context) throws Exception {
                group = new ThreadGroup(group, system.environment.name);
                group.setDaemon(true);
                executor = new OSGiThreadPoolBuilder(system).onFactory().withGroup(group).end().withCached().build();
            }

            @Override
            public void stop(BundleContext context) throws Exception {
                Exception errors = new Exception("stopping OSGi executor");
                ExecutorService shutdowningExecutor = executor;
                executor = inlined;
                ThreadGroup shutdowningGroup = group;
                group = shutdowningGroup.getParent();
                try {
                    shutdowningExecutor.shutdown();
                    shutdowningExecutor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException cause) {
                    Thread.currentThread().interrupt();
                    errors.addSuppressed(cause);
                } catch (Exception cause) {
                    errors.addSuppressed(cause);
                }

                try {
                    killup(shutdowningGroup);
                } catch (Exception cause) {
                    errors.addSuppressed(cause);
                }
                if (errors.getSuppressed().length > 0) {
                    throw errors;
                }
            }
        });

    }

    Optional<BundleException> unwrap(Throwable error) {
        if (error instanceof BundleException) {
            return Optional.of((BundleException) error);
        }
        Throwable cause = error.getCause();
        if (cause == null || cause == error) {
            return Optional.empty();
        }
        return unwrap(cause);
    }

    void killup(ThreadGroup group) throws InterruptedException {
        group.interrupt();
        // join
        {
            int count = group.activeCount();
            Thread threads[] = new Thread[count];
            group.enumerate(threads);
            for (Thread thread : threads) {
                if (thread == null) {
                    continue;
                }
                if (thread.isAlive()) {
                    thread.join(1000);
                }
            }
        }
        // check
        if (!group.isDestroyed() && group.activeCount() > 0) {
            int count = group.activeCount();
            Thread threads[] = new Thread[count];
            group.enumerate(threads);
            Set<String> names = new HashSet<>();
            for (Thread thread : threads) {
                if (thread == null) {
                    continue;
                }
                names.add(thread.getName());
            }
            throw new AssertionError("Cannot killup osgi threads : " + names);
        }
    }

}