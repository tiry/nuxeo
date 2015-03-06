package org.nuxeo.osgi.system;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;

final class OSGiEventRelayer {

    final OSGiSystem system;

    final Activation activation;

    final Map<BundleContext, Activation> byContexts = new HashMap<>();

    public OSGiEventRelayer(OSGiSystem system) {
        this.system = system;
        activation = new Activation(system.bundle);
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new OSGiBundleAdapter.BundleAdapter<OSGiEventRelayer>() {

                    @Override
                    public Class<OSGiEventRelayer> typeof() {
                        return OSGiEventRelayer.class;
                    }

                    @Override
                    public OSGiEventRelayer adapt(Bundle bundle) throws BundleException {
                        return OSGiEventRelayer.this;
                    }
                });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<Activation>() {

            @Override
            public Class<Activation> typeof() {
                return Activation.class;
            }

            @Override
            public Activation adapt(Bundle bundle) {
                if (bundle == system.bundle) {
                    return activation;
                }
                BundleContext context = bundle.getBundleContext();
                Activation activation = byContexts.get(context);
                if (activation != null) {
                    return activation;
                }
                return new Activation(bundle);
            }

        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<FrameworkListener>() {

            @Override
            public Class<FrameworkListener> typeof() {
                return FrameworkListener.class;
            }

            @Override
            public FrameworkListener adapt(Bundle bundle) {
                return bundle.adapt(Activation.class).adapt(FrameworkListener.class);
            }
        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<BundleListener>() {

            @Override
            public Class<BundleListener> typeof() {
                return BundleListener.class;
            }

            @Override
            public BundleListener adapt(Bundle bundle) {
                return bundle.adapt(Activation.class).adapt(BundleListener.class);
            }
        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<ServiceListener>() {

            @Override
            public Class<ServiceListener> typeof() {
                return ServiceListener.class;
            }

            @Override
            public ServiceListener adapt(Bundle bundle) {
                return bundle.adapt(Activation.class).adapt(ServiceListener.class);
            }
        });
    }

    class Activation {
        final Bundle bundle;

        final Map<Class<?>, Relayer<?>> adapters = new HashMap<>();

        Activation(Bundle bundle) {
            this.bundle = bundle;
            adapters.put(FrameworkListener.class, new FrameworkRelayer());
            adapters.put(BundleListener.class, new BundleRelayer());
            adapters.put(ServiceListener.class, new ServiceRelayer());
            adapters.put(ConfigurationListener.class, new ConfigurationRelayer());
        }

        void install() {
            if (bundle == system.bundle) {
                return;
            }
            byContexts.put(bundle.getBundleContext(), this);
            Activation systemRelay = system.adapt(Activation.class);
            systemRelay.add(BundleListener.class, bundle.adapt(BundleListener.class));
            systemRelay.add(FrameworkListener.class, bundle.adapt(FrameworkListener.class));
            systemRelay.add(ServiceListener.class, bundle.adapt(ServiceListener.class));
        }

        void uninstall() {
            if (bundle == system.bundle) {
                return;
            }
            Activation systemRelay = system.adapt(Activation.class);
            systemRelay.remove(BundleListener.class, bundle.adapt(BundleListener.class));
            systemRelay.remove(FrameworkListener.class, bundle.adapt(FrameworkListener.class));
            systemRelay.remove(ServiceListener.class, bundle.adapt(ServiceListener.class));

            byContexts.remove(bundle.getBundleContext());
        }

        <T> T adapt(Class<T> type) {
            return type.cast(adapters.get(type).invoker());
        }

        @SuppressWarnings("unchecked")
        <T> void add(Class<T> type, T... listeners) {
            ((Relayer<T>) adapters.get(type)).add(listeners);
        }

        @SuppressWarnings("unchecked")
        <T> void remove(Class<T> type, T... listeners) {
            ((Relayer<T>) adapters.get(type)).remove(listeners);
        }

        abstract class Relayer<T> {
            List<T> listeners = new CopyOnWriteArrayList<>();

            void add(@SuppressWarnings("unchecked") T... others) {
                listeners.addAll(Arrays.asList(others));
            }

            void remove(@SuppressWarnings("unchecked") T... others) {
                listeners.removeAll(Arrays.asList(others));
            }

            abstract T invoker();
        }

        class FrameworkRelayer extends Relayer<FrameworkListener> {

            final FrameworkListener invoker = new FrameworkListener() {

                @Override
                public void frameworkEvent(FrameworkEvent event) {
                    for (FrameworkListener each : listeners) {
                        each.frameworkEvent(event);
                    }
                }
            };

            @Override
            FrameworkListener invoker() {
                return invoker;
            }

        }

        class BundleRelayer extends Relayer<BundleListener> {

            final BundleListener invoker = new BundleListener() {

                @Override
                public void bundleChanged(BundleEvent event) {
                    for (BundleListener each : listeners) {
                        each.bundleChanged(event);
                    }
                }
            };

            @Override
            BundleListener invoker() {
                return invoker;
            }

        }

        class ServiceRelayer extends Relayer<ServiceListener> {

            final ServiceListener invoker = new ServiceListener() {

                @Override
                public void serviceChanged(ServiceEvent event) {
                    for (ServiceListener each : listeners) {
                        each.serviceChanged(event);
                    }
                }
            };

            @Override
            ServiceListener invoker() {
                return invoker;
            }

        }

        class ConfigurationRelayer extends Relayer<ConfigurationListener> {

            final ConfigurationListener invoker = new ConfigurationListener() {

                @Override
                public void configurationEvent(ConfigurationEvent event) {
                    for (ConfigurationListener each : listeners) {
                        each.configurationEvent(event);
                    }
                }
            };

            @Override
            ConfigurationListener invoker() {
                return invoker;
            }

        }

    }

    BundleActivator activator = new BundleActivator() {

        @Override
        public void start(BundleContext context) throws Exception {
            context.getBundle().adapt(FrameworkListener.class).frameworkEvent(
                    new FrameworkEvent(FrameworkEvent.STARTED, context.getBundle(), null));
        }

        @Override
        public void stop(BundleContext context) throws Exception {
            context.getBundle().adapt(FrameworkListener.class).frameworkEvent(
                    new FrameworkEvent(FrameworkEvent.STOPPED, context.getBundle(), null));
        }
    };

    <T> T adapt(Class<T> type) {
        return type.cast(activator);
    }

}