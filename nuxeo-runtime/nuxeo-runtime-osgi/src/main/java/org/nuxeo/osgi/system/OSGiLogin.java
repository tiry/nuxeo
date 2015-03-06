package org.nuxeo.osgi.system;

import java.util.HashSet;
import java.util.Set;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class OSGiLogin {
    OSGiSystem system;

    final Set<Configuration> activations = new HashSet<>();

    final Configuration service = new Configuration() {

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            for (Configuration each : activations) {
                AppConfigurationEntry[] entries = each.getAppConfigurationEntry(name);
                if (entries != null) {
                    return entries;
                }
            }
            return null;
        }

    };

    ServiceTracker<Configuration, Configuration> tracker;

    public OSGiLogin(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<OSGiLogin>() {

            @Override
            public Class<OSGiLogin> typeof() {
                return OSGiLogin.class;
            }

            @Override
            public OSGiLogin adapt(Bundle bundle) throws BundleException {
                return OSGiLogin.this;
            }
        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<Configuration>() {

            @Override
            public Class<Configuration> typeof() {
                return Configuration.class;
            }

            @Override
            public Configuration adapt(Bundle bundle) throws BundleException {
                return service;
            }
        });
    }

    <T> T adapt(Class<T> type) {
        return type.cast(new BundleActivator() {

            @Override
            public void start(BundleContext context) throws Exception {
                tracker = new ServiceTracker<>(system.getBundleContext(), Configuration.class,
                        new ServiceTrackerCustomizer<Configuration, Configuration>() {

                    @Override
                    public Configuration addingService(ServiceReference<Configuration> reference) {
                        Configuration config = context.getService(reference);
                        activations.add(config);
                        return config;
                    }

                    @Override
                    public void modifiedService(ServiceReference<Configuration> reference, Configuration activation) {
                        ;
                    }

                    @Override
                    public void removedService(ServiceReference<Configuration> reference, Configuration activation) {
                        activations.remove(activation);
                        context.ungetService(reference);
                    }

                });
                tracker.open();
            }

            @Override
            public void stop(BundleContext context) throws Exception {
                try {
                    tracker.close();
                } finally {
                    tracker = null;
                }
            }
        });
    }
}
