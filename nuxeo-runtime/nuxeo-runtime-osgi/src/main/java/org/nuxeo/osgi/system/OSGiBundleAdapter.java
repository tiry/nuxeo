package org.nuxeo.osgi.system;

import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class OSGiBundleAdapter {

    final OSGiSystem system;

    final Map<Class<?>, BundleAdapter<?>> byTypes = new HashMap<>();

    public OSGiBundleAdapter(OSGiSystem system) {
        this.system = system;
        byTypes.put(Activation.class, new BundleAdapter<Activation>() {

            @Override
            public Class<Activation> typeof() {
                return Activation.class;
            }

            @Override
            public Activation adapt(Bundle bundle) {
                return new Activation(bundle);
            }
        });
        byTypes.put(OSGiSystem.class, new BundleAdapter<OSGiSystem>() {

            @Override
            public Class<OSGiSystem> typeof() {
                return OSGiSystem.class;
            }

            @Override
            public OSGiSystem adapt(Bundle bundle) {
                return system;
            }
        });
    }

    interface BundleAdapter<T> {

        Class<T> typeof();

        T adapt(Bundle bundle) throws BundleException;
    }

    class Activation {
        final Bundle bundle;

        Activation(Bundle bundle) {
            this.bundle = bundle;
        }

        <T> BundleAdapter<T> install(BundleAdapter<T> adapter) {
            byTypes.put(adapter.typeof(), adapter);
            return adapter;
        }

        <T> T adapt(Class<T> type, Bundle bundle) throws BundleException {
            @SuppressWarnings("unchecked")
            BundleAdapter<T> bundleAdapter = (BundleAdapter<T>) byTypes.get(type);
            return bundleAdapter.adapt(bundle);
        }
    }

}
