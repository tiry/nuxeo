package org.nuxeo.runtime.logging.jcl;

import org.nuxeo.runtime.logging.LoggingConfigurator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.ILoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;

public class LogAdapterActivator implements BundleActivator {

    static ILoggerFactory factory = new NOPLoggerFactory();

    private ServiceTracker<LoggingConfigurator, Bundle> tracker;

    @Override
    public void start(BundleContext context) throws Exception {
        tracker = new ServiceTracker<>(context, LoggingConfigurator.class,
                new ServiceTrackerCustomizer<LoggingConfigurator, Bundle>() {

                    @Override
                    public Bundle addingService(ServiceReference<LoggingConfigurator> reference) {
                        factory = context.getService(reference).getFactory();
                        return reference.getBundle();
                    }

                    @Override
                    public void modifiedService(ServiceReference<LoggingConfigurator> reference, Bundle service) {
                        ;
                    }

                    @Override
                    public void removedService(ServiceReference<LoggingConfigurator> reference, Bundle service) {
                        factory = new NOPLoggerFactory();
                        context.ungetService(reference);
                    }
                });
        tracker.open();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        tracker.close();
    }

}
