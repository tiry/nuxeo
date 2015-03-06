package org.nuxeo.runtime.logging.logback;

import org.nuxeo.runtime.logging.LoggingConfigurator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class LogbackActivator implements BundleActivator {

    ServiceRegistration<LoggingConfigurator> registration;

    @Override
    public void start(BundleContext context) throws Exception {
        registration = context.registerService(LoggingConfigurator.class, new LogbackConfigurator(), null);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        registration.unregister();
    }

}
