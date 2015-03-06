package org.nuxeo.osgi.system;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.osgi.bootstrap.OSGiBootstrap;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Resource;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;

public class OSGiLifecycle {

    final OSGiSystem system;

    final State UNINSTALLED = new Uninstalled();

    final State INSTALLED = new Installed();

    final State RESOLVING = new Resolving();

    final State RESOLVED = new Resolved();

    final State STARTING = new Starting();

    final State ACTIVE = new Active();

    final State STOPPING = new Stopping();

    final State UNRESOLVING = new Unresolving();

    final Map<Bundle, StateMachine> byBundles = new HashMap<>();

    public OSGiLifecycle(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<Transitions>() {

            @Override
            public Class<Transitions> typeof() {
                return Transitions.class;
            }

            @Override
            public Transitions adapt(Bundle bundle) {
                final StateMachine sm = bundle.adapt(StateMachine.class);
                return bundle.adapt(OSGiExecutor.Adapter.class).adapt(Transitions.class, sm.transitions);
            }

        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<StateMachine>() {

            @Override
            public Class<StateMachine> typeof() {
                return StateMachine.class;
            }

            @Override
            public StateMachine adapt(Bundle bundle) {
                if (!byBundles.containsKey(bundle)) {
                    return new StateMachine(bundle);
                }
                return byBundles.get(bundle);
            }

        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<BundleContext>() {

            @Override
            public Class<BundleContext> typeof() {
                return BundleContext.class;
            }

            @Override
            public BundleContext adapt(Bundle bundle) {
                return byBundles.get(bundle).adapt(BundleContext.class);
            }

        });
    }

    @Override
    public String toString() {
        return "OSGiLifecycle@" + Integer.toHexString(hashCode()) + byBundles;
    }

    interface Transitions {

        void install() throws BundleException;

        void resolve(ResolverHook hook) throws BundleException;

        void start(int options) throws BundleException;

        void stop(int options) throws BundleException;

        void unresolve() throws BundleException;

        void uninstall() throws BundleException;

        <T> T adapt(Class<T> type);
    }

    class StateMachine {

        final Bundle bundle;

        final Transitions transitions;

        State current = UNINSTALLED;

        long lastmodified = System.currentTimeMillis();

        StateMachine(Bundle bundle) {
            this.bundle = bundle;
            transitions = new Transitions() {

                @Override
                public void install() throws BundleException {
                    current.install(StateMachine.this);
                }

                @Override
                public void resolve(ResolverHook hook) throws BundleException {
                    current.resolve(StateMachine.this, hook);
                }

                @Override
                public void start(int options) throws BundleException {
                    current.start(StateMachine.this, options);
                }

                @Override
                public void unresolve() throws BundleException {
                    current.unresolve(StateMachine.this);
                }

                @Override
                public void uninstall() throws BundleException {
                    current.uninstall(StateMachine.this);
                }

                @Override
                public void stop(int options) throws BundleException {
                    current.stop(StateMachine.this, options);
                }

                @Override
                public <T> T adapt(Class<T> type) {
                    if (type.isAssignableFrom(Bundle.class)) {
                        return type.cast(bundle);
                    }
                    if (type.isAssignableFrom(StateMachine.class)) {
                        return type.cast(StateMachine.this);
                    }
                    return null;
                }
            };
        }

        public boolean isInstalled() {
            return current == INSTALLED;
        }

        boolean isResolved() {
            return current == RESOLVED;
        }

        boolean isStarting() {
            return current == STARTING;
        }

        boolean isActive() {
            return current == ACTIVE;
        }

        boolean isStopping() {
            return current == STOPPING;
        }

        boolean isUninstalled() {
            return current == UNINSTALLED;
        }

        @Override
        public String toString() {
            return "LifeCycle [" + bundle.getSymbolicName() + ", " + current + "]";
        }

        long lastmodified() {
            return lastmodified;
        }

        int toOSGi() {
            return current.toOSGi();
        }

        public <T> T adapt(Class<T> type) {
            if (type.isAssignableFrom(Bundle.class)) {
                return type.cast(bundle);
            }
            return bundle.adapt(type);
        }

        State stateChanged(State from, State to, int event) {
            current = to;
            lastmodified = System.currentTimeMillis();
            system.adapt(BundleListener.class).bundleChanged(new BundleEvent(event, bundle));
            System.out.println(bundle);
            return current;
        }

        void setupContext() throws BundleException {
            bundle.adapt(OSGiLoader.Activation.class).install();
            bundle.adapt(OSGiEventRelayer.Activation.class).install();
            bundle.adapt(OSGiService.Activation.class).install();
        }

        void disposeContext() throws BundleException {
            TryCompanion.<Void> of(BundleException.class)
                    .sneakyForEachAndCollect(
                            bundle.adapt(BundleWiring.class)
                                    .getProvidedWires(null)
                                    .stream()
                                    .map(wire -> wire.getRequirer()
                                            .getBundle())
                                    .distinct(),
                            bundle -> bundle.stop())
                    .sneakyRun(() -> bundle.adapt(OSGiActivator.Activation.class)
                            .uninstall())
                    .sneakyRun(() -> bundle.adapt(OSGiEventRelayer.Activation.class)
                            .uninstall())
                    .sneakyRun(() -> bundle.adapt(OSGiService.Activation.class)
                            .uninstall())
                    .sneakyRun(() -> bundle.adapt(OSGiLoader.Activation.class)
                            .reset())
                    .orElseThrow(() -> new BundleException("Caught errors while disposing context of " + bundle));
        }

        void resolved() throws BundleException {
            if ((bundle.getState() & Bundle.RESOLVED) != 0) { // system is resolved twice
                if (bundle != system.bundle) {
                    throw new BundleException(bundle + " is already resolved", BundleException.RESOLVE_ERROR, null);
                }
                bundle.adapt(OSGiLoader.Activation.class).uninstall();
                bundle.adapt(OSGiLoader.Activation.class).install();
                bundle.adapt(OSGiBootstrap.class).resetContext(bundle.adapt(OSGiLoader.Activation.class).context);
                return;
            }
            if ((bundle.adapt(BundleRevision.class).getTypes()&BundleRevision.TYPE_FRAGMENT) == 0) {
                try {
                    setupContext();
                } catch (final BundleException cause) {
                    throw cause;
                }
            }
            stateChanged(current, RESOLVED, BundleEvent.RESOLVED);
        }

    }

    abstract class State {

        State install(StateMachine self) throws BundleException {
            throw new IllegalStateException(self.toString());
        }

        State uninstall(StateMachine self) throws BundleException {
            throw new IllegalStateException(self.toString());
        }

        State unresolve(StateMachine self) throws BundleException {
            throw new IllegalStateException(self.toString());
        }

        State resolve(StateMachine self, ResolverHook hook) throws BundleException {
            throw new IllegalStateException(self.toString());
        }

        State resolve(StateMachine self) throws BundleException {
            final ResolverHookFactory factory = self.bundle.adapt(ResolverHookFactory.class);
            final ResolverHook hook = factory.begin(Collections.singleton(self.bundle.adapt(BundleRevision.class)));
            try {
                return self.current.resolve(self, hook);
            } finally {
                hook.end();
            }
        }

        State start(StateMachine self, int options) throws BundleException {
            throw new IllegalStateException(self.toString());
        }

        State stop(StateMachine self, int options) throws BundleException {
            throw new IllegalStateException(self.toString());
        }

        abstract int toOSGi();

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    class Uninstalled extends State {

        @Override
        public State install(StateMachine self) throws BundleException {
            byBundles.put(self.bundle, self);
            self.adapt(OSGiRepository.Revision.class).install();
            self.stateChanged(UNINSTALLED, INSTALLED, BundleEvent.INSTALLED);
            self.adapt(OSGiStartLevel.Activation.class).install();
            return self.current;
        }

        @Override
        public State resolve(StateMachine self, ResolverHook hook) throws BundleException {
            return install(self).resolve(self, hook);
        }

        @Override
        public State start(StateMachine self, int options) throws BundleException {
            return install(self).resolve(self).start(self, options);
        }

        @Override
        public int toOSGi() {
            return Bundle.UNINSTALLED;
        }

    }

    class Installed extends State {

        @Override
        public State uninstall(StateMachine self) throws BundleException {
            self.stateChanged(INSTALLED, UNINSTALLED, BundleEvent.UNINSTALLED);
            try {
                self.adapt(OSGiStartLevel.Activation.class).uninstall();
                self.adapt(OSGiRepository.Revision.class).uninstall();
            } finally {
                byBundles.remove(self.adapt(Bundle.class));
            }
            return UNINSTALLED;
        }

        @Override
        public State resolve(StateMachine self, ResolverHook hook) throws BundleException {
            class WiringCallback {
                void resolved(Resource resource) throws BundleException {
                    byBundles.get(((BundleRevision) resource).getBundle()).resolved();
                }
            }
            self.current = RESOLVING;
            self.stateChanged(INSTALLED, RESOLVING, BundleEvent.INSTALLED);
            try {
                self.adapt(OSGiWiring.Module.class).install(hook, new WiringCallback()::resolved);
            } catch (final ResolutionException cause) {
                self.stateChanged(RESOLVING, INSTALLED, BundleEvent.INSTALLED);
                throw new BundleException("Cannot resolve bundle " + self.bundle, BundleException.RESOLVE_ERROR, cause);
            }
            return RESOLVED;
        }

        @Override
        public State start(StateMachine self, int options) throws BundleException {
            return resolve(self).start(self, options);
        }

        @Override
        public int toOSGi() {
            return Bundle.INSTALLED;
        }

    }

    class Resolving extends State {

        @Override
        public int toOSGi() {
            return Bundle.INSTALLED;
        }

        @Override
        public State resolve(StateMachine self, ResolverHook hook) throws BundleException {
            return this;
        }

    }

    class Resolved extends State {

        @Override
        public State resolve(StateMachine self) throws BundleException {
            return RESOLVED;
        }

        @Override
        public State resolve(StateMachine self, ResolverHook hook) throws BundleException {
            return RESOLVED;
        }

        @Override
        public State start(StateMachine self, int options) throws BundleException {
            if ((self.bundle.adapt(BundleRevision.class).getTypes()&BundleRevision.TYPE_FRAGMENT) != 0) {
                return RESOLVED;
            }
            TryCompanion.of(BundleException.class).sneakyForEach(self.adapt(ResolveContext.class)
                    .getWirings()
                    .keySet()
                    .stream()
                    .map(BundleRevision.class::cast)
                    .map(BundleRevision::getBundle), Bundle::start);
            self.stateChanged(RESOLVED, STARTING, BundleEvent.STARTING);
            final OSGiActivator.Activation activation = self.adapt(OSGiActivator.Activation.class);
            if (activation.isLazy(options)) {
                return STARTING;
            }
            try {
                activation.install(Constants.BUNDLE_ACTIVATOR);
            } catch (final BundleException cause) {
                try {
                    stop(self, 0);
                } catch (final BundleException otherCause) {
                    cause.addSuppressed(otherCause);
                }
                throw cause;
            }
            return self.stateChanged(STARTING, ACTIVE, BundleEvent.STARTED);
        }

        @Override
        public State stop(StateMachine self, int options) throws BundleException {
            return RESOLVED;
        }

        @Override
        public State unresolve(StateMachine self) throws BundleException {
            self.stateChanged(RESOLVED, UNRESOLVING, BundleEvent.RESOLVED);
            try {
                self.adapt(OSGiWiring.Module.class).uninstall();
            } catch (final BundleException cause) {
                self.adapt(FrameworkListener.class)
                        .frameworkEvent(new FrameworkEvent(FrameworkEvent.ERROR, self.bundle, cause));
            } finally {
                if ((self.bundle.adapt(BundleRevision.class).getTypes() & BundleRevision.TYPE_FRAGMENT) != 0) {
                    self.adapt(OSGiLoader.Activation.class).uninstall();
                }
            }
            return self.stateChanged(UNRESOLVING, INSTALLED, BundleEvent.UNRESOLVED);
        }

        @Override
        public State uninstall(StateMachine self) throws BundleException {
            return unresolve(self).uninstall(self);
        }

        @Override
        public int toOSGi() {
            return Bundle.INSTALLED | Bundle.RESOLVED;
        }
    }

    class Starting extends State {

        @Override
        public State resolve(StateMachine self) throws BundleException {
            return STARTING;
        }

        @Override
        public State resolve(StateMachine self, ResolverHook hook) throws BundleException {
            return STARTING;
        }

        @Override
        public State start(StateMachine self, int options) throws BundleException {
            return STARTING;
        }

        @Override
        public State stop(StateMachine self, int options) throws BundleException {
            self.stateChanged(STARTING, STOPPING, BundleEvent.STOPPING);
            try {
                self.disposeContext();
            } finally {
                self.stateChanged(STOPPING, RESOLVED, BundleEvent.RESOLVED);
            }
            return self.current;
        }

        @Override
        public State unresolve(StateMachine self) throws BundleException {
            return stop(self, 0).unresolve(self);
        }

        @Override
        public int toOSGi() {
            return Bundle.INSTALLED | Bundle.RESOLVED | Bundle.STARTING;
        }

    }

    class Active extends State {

        @Override
        public State uninstall(StateMachine self) throws BundleException {
            return unresolve(self).uninstall(self);
        }

        @Override
        public State resolve(StateMachine self) throws BundleException {
            return ACTIVE;
        }

        @Override
        public State resolve(StateMachine self, ResolverHook hook) throws BundleException {
            return ACTIVE;
        }

        @Override
        public State unresolve(StateMachine self) throws BundleException {
            return stop(self, 0).unresolve(self);
        }

        @Override
        public State start(StateMachine self, int options) throws BundleException {
            return ACTIVE;
        }

        @Override
        public State stop(StateMachine self, int options) throws BundleException {
            self.stateChanged(ACTIVE, STOPPING, BundleEvent.STOPPING);
            final BundleException errors = new BundleException(this.toString());
            try {
                self.disposeContext();
            } catch (final BundleException cause) {
                errors.addSuppressed(cause);
                throw errors;
            } finally {
                self.stateChanged(STOPPING, RESOLVED, BundleEvent.STOPPED);
            }
            return self.current;
        }

        @Override
        public int toOSGi() {
            return Bundle.INSTALLED | Bundle.RESOLVED | Bundle.ACTIVE;
        }
    }

    class Stopping extends State {

        @Override
        public int toOSGi() {
            return Bundle.INSTALLED | Bundle.RESOLVED | Bundle.STOPPING;
        }

        @Override
        public State start(StateMachine self, int options) throws BundleException {
            return STOPPING;
        }

        @Override
        public State stop(StateMachine self, int options) throws BundleException {
            return STOPPING;
        }
    }

    class Unresolving extends State {

        @Override
        public int toOSGi() {
            return Bundle.RESOLVED;
        }

        @Override
        public State install(StateMachine self) throws BundleException {
            throw new IllegalStateException(self.bundle.toString());
        }

        @Override
        public State resolve(StateMachine self, ResolverHook hook) throws BundleException {
            throw new IllegalStateException(self.bundle.toString());
        }

        @Override
        public State start(StateMachine self, int options) throws BundleException {
            throw new IllegalStateException(self.bundle.toString());
        }

        @Override
        public State stop(StateMachine self, int options) throws BundleException {
            throw new IllegalStateException(self.bundle.toString());
        }

        @Override
        public State unresolve(StateMachine self) throws BundleException {
            return this;
        }

        @Override
        public State uninstall(StateMachine self) throws BundleException {
            throw new IllegalStateException(self.bundle.toString());
        }

    }

    public String toString(Bundle bundle) {
        return byBundles.get(bundle).toString();
    }

}
