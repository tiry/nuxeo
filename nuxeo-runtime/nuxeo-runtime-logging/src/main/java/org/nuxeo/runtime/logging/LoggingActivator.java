package org.nuxeo.runtime.logging;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class LoggingActivator implements BundleActivator {

    static LoggingActivator self;

    final LogListener relayer = new LogListener() {

        @Override
        public void logged(LogEntry entry) {
            Log log = LogFactory.getLog(entry.getBundle().getSymbolicName());
            switch (entry.getLevel()) {
                case LogService.LOG_INFO:
                    log.info(entry.getMessage(), entry.getException());
                    break;
                case LogService.LOG_DEBUG:
                    log.debug(entry.getMessage(), entry.getException());
                    break;
                case LogService.LOG_ERROR:
                    log.error(entry.getMessage(), entry.getException());
                    break;
                case LogService.LOG_WARNING:
                    log.warn(entry.getMessage(), entry.getException());
                    break;
            }
        }
    };

    LoggingConfigurator configurator;

    ServiceTracker<LoggingConfigurator, Bundle> tracker;

    ServiceRegistration<LoggingConfigurator> registration;

    ServiceRegistration<ManagedService> justintime;

    @Override
    public void start(BundleContext context) throws Exception {
        self = this;
        tracker = new ServiceTracker<>(context, LoggingConfigurator.class,
                new ServiceTrackerCustomizer<LoggingConfigurator, Bundle>() {

                    @Override
                    public Bundle addingService(ServiceReference<LoggingConfigurator> reference) {
                        configurator = context.getService(reference);
                        configurator.init();
                        { // register osgi events relayer
                            ServiceReference<LogReaderService> ref = context
                                    .getServiceReference(LogReaderService.class);
                            try {
                                context.getService(ref).addLogListener(relayer);
                            } finally {
                                context.ungetService(ref);
                            }
                        }
                        { // register justintime configuration listener
                            Dictionary<String, Object> dict = new Hashtable<>();
                            dict.put("service.pid", "org.nuxeo.runtime.logging");
                            dict.put("justintime", "on");
                            justintime = context.registerService(ManagedService.class, new ManagedService() {

                                @Override
                                public void updated(Dictionary<String, ?> dict) throws ConfigurationException {
                                    String mode = (String) dict.get("justintime");
                                    if ("off".equals(mode)) {
                                        LoggingConfigurator.Scope.APPLICATION_SCOPE.get().forget("none");
                                    } else if ("on".equals(mode)) {
                                        configurator.justintime(LoggingConfigurator.Scope.APPLICATION_SCOPE);
                                    }
                                }
                            }, dict);
                        }
                        return reference.getBundle();
                    }

                    @Override
                    public void modifiedService(ServiceReference<LoggingConfigurator> reference, Bundle service) {
                        ;
                    }

                    @Override
                    public void removedService(ServiceReference<LoggingConfigurator> reference, Bundle service) {
                        { // unregister osgi events relayer
                            ServiceReference<LogReaderService> ref = context
                                    .getServiceReference(LogReaderService.class);
                            try {
                                context.getService(ref).removeLogListener(relayer);
                            } finally {
                                context.ungetService(ref);
                            }
                        }
                        { // unregister justintime configuration listener
                            justintime.unregister();
                        }
                        configurator.reset();
                        context.ungetService(reference);
                        configurator = null;
                    }

                });
        tracker.open();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        tracker.close();
    }

}
