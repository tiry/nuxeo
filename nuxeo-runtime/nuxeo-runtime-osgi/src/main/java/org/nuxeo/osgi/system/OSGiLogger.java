package org.nuxeo.osgi.system;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;

public class OSGiLogger {

    final OSGiSystem system;

    interface Relayer {
        void relay(LogEntry entry);

        LogReaderService reader();
    }

    final Relayer CACHING = new Relayer() {

        List<LogEntry> entries = new LinkedList<LogEntry>();

        final LogReaderService reader = new LogReaderService() {

            @Override
            public void removeLogListener(LogListener listener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Enumeration<LogEntry> getLog() {
                Enumeration<LogEntry> enumerated = Collections.enumeration(entries);
                entries = new LinkedList<>();
                return enumerated;
            }

            @Override
            public void addLogListener(LogListener listener) {
                throw new UnsupportedOperationException();
            }
        };

        @Override
        public void relay(LogEntry entry) {
            entries.add(entry);
        }

        @Override
        public LogReaderService reader() {
            return reader;
        }

    };

    final Relayer SERVICE = new Relayer() {

        final Set<LogListener> listeners = new HashSet<>();

        final LogReaderService reader = new LogReaderService() {

            @Override
            public void addLogListener(LogListener listener) {
                listeners.add(listener);
                if (relayer == CACHING) {
                    relayer = SERVICE;
                    Enumeration<?> log = CACHING.reader().getLog();
                    while (log.hasMoreElements()) {
                        relay((LogEntry) log.nextElement());
                    }
                }
            }

            @Override
            public void removeLogListener(LogListener listener) {
                listeners.remove(listener);
                if (listeners == null) {
                    relayer = CACHING;
                }
            }

            @Override
            public Enumeration<LogEntry> getLog() {
                throw new UnsupportedOperationException();
            }
        };

        @Override
        public LogReaderService reader() {
            return reader;
        };

        @Override
        public void relay(LogEntry entry) {
            for (LogListener each : listeners) {
                each.logged(entry);
            }
        }

    };

    Relayer relayer = CACHING;

    void relay(Bundle bundle, ServiceReference<?> sr, int level, String message, Throwable exception) {
        CachedEntry entry = new CachedEntry(bundle, sr, level, message, exception);
        relayer.relay(entry);
    }

    final ServiceFactory<LogService> factory = new ServiceFactory<LogService>() {

        @Override
        public LogService getService(Bundle bundle, ServiceRegistration<LogService> registration) {
            return new LogService() {

                @Override
                public void log(@SuppressWarnings("rawtypes") ServiceReference sr, int level, String message,
                        Throwable exception) {
                    relay(bundle, sr, level, message, exception);
                }

                @Override
                public void log(@SuppressWarnings("rawtypes") ServiceReference sr, int level, String message) {
                    relay(bundle, sr, level, message, null);
                }

                @Override
                public void log(int level, String message, Throwable exception) {
                    relay(bundle, null, level, message, exception);
                }

                @Override
                public void log(int level, String message) {
                    relay(bundle, null, level, message, null);
                }

            };
        }

        @Override
        public void ungetService(Bundle bundle, ServiceRegistration<LogService> registration, LogService service) {
            ;
        }

    };

    static class CachedEntry implements LogEntry {

        final Bundle bundle;

        final ServiceReference<?> reference;

        final int level;

        final String message;

        final Throwable exception;

        final long timestamp = 0L;

        CachedEntry(Bundle bundle, ServiceReference<?> reference, int level, String message, Throwable exception) {
            super();
            this.bundle = bundle;
            this.reference = reference;
            this.level = level;
            this.message = message;
            this.exception = exception;
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public ServiceReference getServiceReference() {
            return reference;
        }

        @Override
        public int getLevel() {
            return level;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public Throwable getException() {
            return exception;
        }

        @Override
        public long getTime() {
            return timestamp;
        }

    }

    OSGiLogger(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<OSGiLogger>() {

            @Override
            public Class<OSGiLogger> typeof() {
                return OSGiLogger.class;
            }

            @Override
            public OSGiLogger adapt(Bundle bundle) {
                return OSGiLogger.this;
            }
        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<LogService>() {

            @Override
            public Class<LogService> typeof() {
                return LogService.class;
            }

            @Override
            public LogService adapt(Bundle bundle) {
                return new LogService() {

                    @Override
                    public void log(@SuppressWarnings("rawtypes") ServiceReference sr, int level, String message,
                            Throwable exception) {
                        relay(bundle, sr, level, message, exception);
                    }

                    @Override
                    public void log(@SuppressWarnings("rawtypes") ServiceReference sr, int level, String message) {
                        relay(bundle, sr, level, message, null);
                    }

                    @Override
                    public void log(int level, String message, Throwable exception) {
                        relay(bundle, null, level, message, exception);
                    }

                    @Override
                    public void log(int level, String message) {
                        relay(bundle, null, level, message, null);
                    }
                };
            }

        });
    }

    final BundleActivator activator = new BundleActivator() {

        ServiceRegistration<LogReaderService> readerRegistration;

        ServiceRegistration<LogService> logRegistration;

        final FrameworkListener frameworkRelayer = new FrameworkListener() {

            @Override
            public void frameworkEvent(FrameworkEvent event) {
                relay(event.getBundle(), null,
                        (event.getType() & FrameworkEvent.ERROR) != 0 ? LogService.LOG_ERROR : LogService.LOG_INFO,
                        event.toString(), event.getThrowable());
            }
        };

        final BundleListener bundleRelayer = new BundleListener() {

            @Override
            public void bundleChanged(BundleEvent event) {
                relay(event.getBundle(), null, LogService.LOG_INFO, event.toString(), null);
            }
        };

        final ServiceListener serviceRelayer = new ServiceListener() {

            @Override
            public void serviceChanged(ServiceEvent event) {
                relay(system, event.getServiceReference(), LogService.LOG_INFO, event.toString(), null);
            }
        };

        @Override
        public void start(BundleContext context) throws Exception {
            readerRegistration = context.registerService(LogReaderService.class, SERVICE.reader(), null);
            context.addBundleListener(bundleRelayer);
            context.addFrameworkListener(frameworkRelayer);
            context.addServiceListener(serviceRelayer);
            logRegistration = context.registerService(LogService.class, factory, null);
        }

        @Override
        public void stop(BundleContext context) throws Exception {
            readerRegistration.unregister();
            context.removeFrameworkListener(frameworkRelayer);
            context.removeBundleListener(bundleRelayer);
            context.removeServiceListener(serviceRelayer);
            logRegistration.unregister();

        }
    };

    <T> T adapt(Class<T> type) {
        return type.cast(activator);
    }

}
