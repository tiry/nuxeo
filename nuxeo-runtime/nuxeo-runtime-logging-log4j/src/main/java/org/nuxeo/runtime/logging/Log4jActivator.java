package org.nuxeo.runtime.logging;

import org.nuxeo.runtime.logging.LoggingConfigurator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Log4jActivator implements BundleActivator {

    ServiceRegistration<LoggingConfigurator> registration;

    @Override
    public void start(BundleContext context) throws Exception {
        registration = context.registerService(LoggingConfigurator.class, new Log4jConfigurator(), null);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        registration.unregister();
    }

}
