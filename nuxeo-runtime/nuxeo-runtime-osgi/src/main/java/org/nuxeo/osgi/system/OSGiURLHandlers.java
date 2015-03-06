package org.nuxeo.osgi.system;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class OSGiURLHandlers {

    OSGiSystem system;

    final Map<String, Activation> byProtocols = new HashMap<>();

    final URLStreamHandlerService service = new AbstractURLStreamHandlerService() {

        @Override
        public URLConnection openConnection(URL u) throws IOException {
            String protocol = u.getProtocol();
            if (!byProtocols.containsKey(protocol)) {
                return null;
            }
            return byProtocols.get(protocol).service.openConnection(u);
        }
    };

    ServiceTracker<URLStreamHandlerService, Activation> tracker;

    public OSGiURLHandlers(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<OSGiURLHandlers>() {

            @Override
            public Class<OSGiURLHandlers> typeof() {
                return OSGiURLHandlers.class;
            }

            @Override
            public OSGiURLHandlers adapt(Bundle bundle) throws BundleException {
                return OSGiURLHandlers.this;
            }
        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<URLStreamHandlerService>() {

            @Override
            public Class<URLStreamHandlerService> typeof() {
                return URLStreamHandlerService.class;
            }

            @Override
            public URLStreamHandlerService adapt(Bundle bundle) throws BundleException {
                return service;
            }
        });
    }

    private BundleActivator activator = new BundleActivator() {

        @Override
        public void start(BundleContext context) throws Exception {
            tracker = new ServiceTracker<>(system.getBundleContext(), URLStreamHandlerService.class,
                    new ServiceTrackerCustomizer<URLStreamHandlerService, Activation>() {

                @Override
                public Activation addingService(ServiceReference<URLStreamHandlerService> reference) {
                    String protocol = (String) reference.getProperty(URLConstants.URL_HANDLER_PROTOCOL);
                    Activation activation = new Activation(protocol, system.getBundleContext().getService(reference));
                    byProtocols.put(protocol, activation);
                    return activation;
                }

                @Override
                public void modifiedService(ServiceReference<URLStreamHandlerService> reference,
                        Activation activation) {
                    ;
                }

                @Override
                public void removedService(ServiceReference<URLStreamHandlerService> reference, Activation activation) {
                    byProtocols.remove(activation.protocol);
                    system.getBundleContext().ungetService(reference);
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
    };

    class Activation {
        final String protocol;

        final URLStreamHandlerService service;

        Activation(String protocol, URLStreamHandlerService service) {
            this.protocol = protocol;
            this.service = service;
        }
    }

    <T> T adapt(Class<T> type) {
        return type.cast(activator);
    }

}
