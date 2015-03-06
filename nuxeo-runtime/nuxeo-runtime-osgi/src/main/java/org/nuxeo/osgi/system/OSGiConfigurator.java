package org.nuxeo.osgi.system;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.cm.ManagedService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

class OSGiConfigurator {

    final OSGiSystem system;

    OSGiConfigurator(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<OSGiConfigurator>() {

            @Override
            public Class<OSGiConfigurator> typeof() {
                return OSGiConfigurator.class;
            }

            @Override
            public OSGiConfigurator adapt(Bundle bundle) {
                return OSGiConfigurator.this;
            }
        });
    }

    class Activation {

        final String pid;

        final Bundle source;

        final ManagedService service;

        final Configuration configuration;

        final Map<String, Object> properties = new HashMap<>();

        Activation(Map<String, Object> defaults, Bundle source, ManagedService service) {
            this.service = service;
            this.source = source;
            pid = (String) defaults.get(Constants.SERVICE_PID);
            properties.putAll(defaults);
            configuration = new Configuration() {

                @Override
                public String getPid() {
                    return pid;
                }

                @Override
                public Dictionary<String, Object> getProperties() {
                    return new Hashtable<String, Object>(properties);
                }

                @Override
                public void update(Dictionary<String, ?> dict) throws IOException {
                    Enumeration<String> keys = dict.keys();
                    while (keys.hasMoreElements()) {
                        String key = keys.nextElement();
                        properties.put(key, dict.get(key));
                    }
                    try {
                        service.updated(dict);
                    } catch (ConfigurationException cause) {
                        source.adapt(FrameworkListener.class)
                                .frameworkEvent(new FrameworkEvent(FrameworkEvent.ERROR, source, cause));
                    }

                    relayer.configurationEvent(new ConfigurationEvent(registration.getReference(),
                            ConfigurationEvent.CM_UPDATED, null, pid));
                }

                @Override
                public void delete() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String getFactoryPid() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void update() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setBundleLocation(String location) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String getBundleLocation() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public long getChangeCount() {
                    throw new UnsupportedOperationException();
                }

            };

        }

    }

    final Map<String, Activation> byPids = new HashMap<>();

    ServiceRegistration<ConfigurationAdmin> registration;

    ServiceTracker<ConfigurationListener, ConfigurationListener> listenerTracker;

    ServiceTracker<ManagedService, Activation> managerTracker;

    ConfigurationListener relayer;

    <T> T adapt(Class<T> type) {
        return type.cast(new BundleActivator() {

            @Override
            public void start(BundleContext context) {

                relayer = context.getBundle()
                        .adapt(OSGiEventRelayer.Activation.class)
                        .adapt(ConfigurationListener.class);

                registration = context.registerService(ConfigurationAdmin.class, new ConfigurationAdmin() {

                    @Override
                    public Configuration createFactoryConfiguration(String factoryPid) throws IOException {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Configuration createFactoryConfiguration(String factoryPid, String location)
                            throws IOException {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Configuration getConfiguration(String pid, String location) throws IOException {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Configuration getConfiguration(String pid) throws IOException {
                        if (!byPids.containsKey(pid)) {
                            throw new FileNotFoundException("unknown " + pid);
                        }
                        return byPids.get(pid).configuration;
                    }

                    @Override
                    public Configuration[] listConfigurations(String filter)
                            throws IOException, InvalidSyntaxException {
                        throw new UnsupportedOperationException();
                    }

                }, null);

                listenerTracker = new ServiceTracker<>(context, ConfigurationListener.class,
                        new ServiceTrackerCustomizer<ConfigurationListener, ConfigurationListener>() {

                    @Override
                    public ConfigurationListener addingService(ServiceReference<ConfigurationListener> reference) {
                        ConfigurationListener service = context.getService(reference);
                        context.getBundle().adapt(OSGiEventRelayer.Activation.class).add(ConfigurationListener.class,
                                service);
                        return service;
                    }

                    @Override
                    public void modifiedService(ServiceReference<ConfigurationListener> reference,
                            ConfigurationListener service) {
                        ;
                    }

                    @Override
                    public void removedService(ServiceReference<ConfigurationListener> reference,
                            ConfigurationListener service) {
                        context.getBundle().adapt(OSGiEventRelayer.Activation.class).remove(ConfigurationListener.class,
                                service);
                    }
                });
                listenerTracker.open();

                managerTracker = new ServiceTracker<>(context, ManagedService.class,
                        new ServiceTrackerCustomizer<ManagedService, Activation>() {

                    @Override
                    public Activation addingService(ServiceReference<ManagedService> reference) {
                        ManagedService service = context.getService(reference);
                        Bundle source = reference.getBundle();
                        Map<String, Object> defaults = new HashMap<>();
                        for (String key : reference.getPropertyKeys()) {
                            defaults.put(key, reference.getProperty(key));
                        }
                        Activation activation = new Activation(defaults, source, service);
                        byPids.put(activation.pid, activation);
                        return activation;
                    }

                    @Override
                    public void modifiedService(ServiceReference<ManagedService> reference, Activation activation) {
                        for (String key : reference.getPropertyKeys()) {
                            activation.properties.put(key, reference.getProperty(key));
                        }
                    }

                    @Override
                    public void removedService(ServiceReference<ManagedService> reference, Activation activation) {
                        byPids.remove(activation.pid);
                        context.ungetService(reference);
                    }
                });
                managerTracker.open();

            }

            @Override
            public void stop(BundleContext context) {
                registration.unregister();
                listenerTracker.close();
                managerTracker.close();
            }
        });
    }

}
