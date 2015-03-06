/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bogdan Stefanescu
 *     Ian Smith
 *     Florent Guillaume
 */

package org.nuxeo.runtime.osgi;

import java.util.Collection;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Optional;
import java.util.Set;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.RuntimeContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

/**
 * @author Bogdan Stefanescu
 * @author Ian Smith
 * @author Florent Guillaume
 */
public class OSGiRuntimeActivator implements BundleActivator {

    static OSGiRuntimeActivator self;

    protected OSGiRuntimeService runtime;

    protected final Login login = new Login();

    protected final Tracking tracking = new Tracking();

    protected final Resolving resolving = new Resolving();

    protected final Configuring configuring = new Configuring();

    @Override
    public void start(BundleContext context) {
        runtime = new OSGiRuntimeService(context);
        Framework.initialize(runtime);
        resolving.start(context);
        configuring.start(context);
        login.start(context);
        tracking.start(context);
    }

    @Override
    public void stop(BundleContext context) {
        try {
            tracking.stop(context);
            resolving.stop(context);
            configuring.stop(context);
            login.stop(context);
            Framework.shutdown();
        } finally {
            runtime = null;
        }
    }

    class Resolving implements BundleActivator {

        protected ServiceRegistration<ResolverHookFactory> resolverRegistration;

        @Override
        public void start(BundleContext context) {
            resolverRegistration = context.registerService(
                    ResolverHookFactory.class,
                    new ResolverHookFactory() {
                        @Override
                        public ResolverHook begin(Collection<BundleRevision> triggers) {
                            return new ResolverHook() {

                                OSGiRuntimeService runtime = (OSGiRuntimeService) Framework.getRuntime();

                                @Override
                                public void filterSingletonCollisions(BundleCapability singleton,
                                        Collection<BundleCapability> collisionCandidates) {
                                    return;
                                }

                                @Override
                                public void filterResolvable(Collection<BundleRevision> candidates) {
                                    for (BundleRevision candidate : candidates) {
                                        OSGiRuntimeContext context = runtime.getContext(candidate.getBundle());
                                        if (context == null) {
                                            continue;
                                        }
                                        try {
                                            context.resolve();
                                        } catch (RuntimeServiceException cause) {
                                            LogFactory.getLog(OSGiRuntimeActivator.class).error(
                                                    "Cannot resolve " + context,
                                                    cause);
                                            candidates.remove(candidate);
                                        }
                                    }
                                }

                                @Override
                                public void filterMatches(BundleRequirement requirement,
                                        Collection<BundleCapability> candidates) {
                                    ;
                                }

                                @Override
                                public void end() {
                                    ;
                                }
                            };
                        }
                    },
                    null);
        }

        @Override
        public void stop(BundleContext context) {
            try {
                resolverRegistration.unregister();
            } finally {
                resolverRegistration = null;
            }
        }

    }

    interface Updater {
        Optional<OSGiRuntimeContext> install(Bundle bundle);

        void destroy(OSGiRuntimeContext context);

        void resolve(OSGiRuntimeContext context);

        void unresolve(OSGiRuntimeContext context);

        void activate(OSGiRuntimeContext context);

        void deactivate(OSGiRuntimeContext context);

    }

    class Tracking implements BundleActivator {

        BundleTracker<OSGiRuntimeContext> tracker;

        final Set<OSGiRuntimeContext> deferred = new HashSet<>();

        final Updater perform = new Updater() {

            @Override
            public Optional<OSGiRuntimeContext> install(Bundle bundle) {
                return Optional.ofNullable(runtime.installBundle(bundle));
            }

            @Override
            public void destroy(OSGiRuntimeContext context) {
                context.destroy();
            }

            @Override
            public void resolve(OSGiRuntimeContext context) {
                context.resolve();
            }

            @Override
            public void unresolve(OSGiRuntimeContext context) {
                context.unresolve();
            }

            @Override
            public void activate(OSGiRuntimeContext context) {
                context.activate();
            }

            @Override
            public void deactivate(OSGiRuntimeContext context) {
                context.deactivate();
            }

        };

        final Updater defer = new Updater() {

            @Override
            public Optional<OSGiRuntimeContext> install(Bundle bundle) {
                Optional<OSGiRuntimeContext> optional = Optional.of(runtime.installBundle(bundle));
                optional.ifPresent(context -> deferred.add(context));
                return optional;
            }

            @Override
            public void destroy(OSGiRuntimeContext context) {
                deferred.add(context);
            }

            @Override
            public void resolve(OSGiRuntimeContext context) {
                deferred.add(context);
            }

            @Override
            public void unresolve(OSGiRuntimeContext context) {
                deferred.add(context);
            }

            @Override
            public void activate(OSGiRuntimeContext context) {
                deferred.add(context);
            }

            @Override
            public void deactivate(OSGiRuntimeContext context) {
                deferred.add(context);
            }

        };

        Updater updater = perform;

        void setDefer() {
            updater = defer;
        }

        void setPerform() {
            updater = perform;
            TryCompanion.<Void> of(RuntimeServiceException.class)
                    .forEachAndCollect(deferred.stream(), context -> {
                        int state = context.getBundle().getState();
                        if ((state & Bundle.ACTIVE) != 0) {
                            context.activate();
                        } else if ((state & Bundle.RESOLVED) != 0) {
                            if (context.isActivated()) {
                                context.deactivate();
                            } else {
                                context.resolve();
                            }
                        } else if ((state & Bundle.UNINSTALLED) != 0) {
                            context.unregister();
                        }
                    })
                    .orElseThrow(() -> new RuntimeServiceException("Caught errors while synching deferred components"));
        }

        @Override
        public void start(BundleContext context) {
            tracker = new BundleTracker<OSGiRuntimeContext>(runtime.getBundleContext(),
                    Bundle.RESOLVED | Bundle.STARTING | Bundle.STOPPING | Bundle.ACTIVE,
                    new BundleTrackerCustomizer<OSGiRuntimeContext>() {

                        @Override
                        public OSGiRuntimeContext addingBundle(Bundle bundle, BundleEvent event) {
                            if ((bundle.adapt(BundleRevision.class).getTypes()&BundleRevision.TYPE_FRAGMENT) != 0) {
                                return null;
                            }
                            Optional<OSGiRuntimeContext> optional = updater.install(bundle);
                            optional.ifPresent(runtime -> {
                                if ((bundle.getState() & Bundle.ACTIVE) != 0) {
                                    updater.activate(runtime);
                                }
                            });
                            return optional.orElse(null);
                        }

                        @Override
                        public void modifiedBundle(Bundle bundle, BundleEvent event, OSGiRuntimeContext context) {
                            if ((event.getType() & (BundleEvent.STARTED)) != 0) {
                                updater.activate(context);
                            } else if ((event.getType() & (BundleEvent.STOPPING)) != 0) {
                                updater.deactivate(context);
                            } else if ((event.getType() & (BundleEvent.UNRESOLVED)) != 0) {
                                updater.unresolve(context);
                            } else if ((event.getType() & (BundleEvent.UNINSTALLED)) != 0) {
                                updater.destroy(context);
                            }
                        }

                        @Override
                        public void removedBundle(Bundle bundle, BundleEvent event, OSGiRuntimeContext context) {
                            if (context.getState() != RuntimeContext.UNREGISTERED) {
                                context.destroy();
                            }
                        }
                    });
            tracker.open();
        }

        @Override
        public void stop(BundleContext context) {
            if (tracker == null) {
                return;
            }
            try {
                tracker.close();
            } finally {
                tracker = null;
            }
        }
    }

    class Configuring implements BundleActivator {

        ServiceRegistration<ManagedService> registration;

        @Override
        public void start(BundleContext context) {
            Dictionary<String, Object> dict = new Hashtable<>();
            dict.put("service.pid", "org.nuxeo.runtime.osgi");
            dict.put("synch", "off");
            registration = context.registerService(ManagedService.class, new ManagedService() {

                @Override
                public void updated(Dictionary<String, ?> dict) throws ConfigurationException {
                    String mode = (String) dict.get("synch");
                    if ("off".equals(mode)) {
                        tracking.stop(context);
                    } else if ("on".equals(mode)) {
                        tracking.start(context);
                    } else if ("defer".equals(mode)) {
                        tracking.setDefer();
                        tracking.start(context);
                    } else if ("forget".equals(mode)) {
                        tracking.deferred.clear();
                        tracking.setPerform();
                    } else if ("flush".equals(mode)) {
                        tracking.setPerform();
                    }
                }
            }, dict);
        }

        @Override
        public void stop(BundleContext context) {
            registration.unregister();
        }

    }

    public class Login implements BundleActivator {

        ServiceRegistration<Configuration> registration;

        @Override
        public void start(BundleContext context) {
            Configuration proxy = new Configuration() {

                @Override
                public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                    return Framework.getService(Configuration.class).getAppConfigurationEntry(name);
                }

            };
            registration = context.registerService(Configuration.class, proxy, null);
        }

        @Override
        public void stop(BundleContext context) {
            registration.unregister();
        }

    }

}
