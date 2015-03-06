package org.nuxeo.osgi.system;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

public class OSGiActivator {

    final OSGiSystem system;

    class ActivatorThreadHolder extends ThreadLocal<LazyActivator> {

        @Override
        protected LazyActivator initialValue() {
            return new LazyActivator();
        }
    }

    ActivatorThreadHolder threadHolder = new ActivatorThreadHolder();

    final Map<Bundle, Activation> byBundles = new HashMap<>();

    // pre-load required activation classes
    static {
        Activation.class.getName();
        DeferredActivation.class.getName();
        LazyActivator.class.getName();
        NullActivator.class.getName();
    }

    OSGiActivator(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<Activation>() {

            @Override
            public Class<Activation> typeof() {
                return Activation.class;
            }

            @Override
            public Activation adapt(Bundle bundle) {
                if (!byBundles.containsKey(bundle)) {
                    return new Activation(bundle);
                }
                return byBundles.get(bundle);
            }

        });
    }

    @Override
    public String toString() {
        return "OSGiActivator@" + Integer.toHexString(hashCode()) + byBundles;
    }

    class Activation {

        final Bundle bundle;

        final BundleWiring wiring;

        BundleActivator activator;

        boolean activating;

        Activation(Bundle bundle) {
            this.bundle = bundle;
            wiring = bundle.adapt(BundleRevision.class).getWiring();
        }

        BundleActivator loadActivator(String directive) throws BundleException {
            String classname = bundle.getHeaders().get(directive);
            if (classname == null) {
                return NULL_ACTIVATOR;
            }
            try {
                Class<?> clazz = bundle.loadClass(classname);
                if (clazz == null) {
                    throw new ClassNotFoundException("Cannot find activator " + classname + " in " + bundle);
                }
                return (BundleActivator) clazz.newInstance();
            } catch (ReflectiveOperationException cause) {
                throw new BundleException("Activator not instantiable: " + classname, cause);
            }
        }

        boolean isLazy(int options) {
            if ((options & Bundle.START_ACTIVATION_POLICY) == 0) {
                return false;
            }
            return Constants.ACTIVATION_LAZY.equals(bundle.getHeaders().get(Constants.BUNDLE_ACTIVATIONPOLICY));
        }

        void install(String directive) throws BundleException {
            if (activating || (activator != null)) {
                return;
            }
            activating = true;
            byBundles.put(bundle, this);
            try {
                activator = loadActivator(directive);
                TryCompanion.<Void> of(BundleException.class)
                        .sneakyRun(() -> activator
                                .start(findHost(wiring)
                                        .getRevision()
                                        .getBundle()
                                        .getBundleContext()))
                        .orElseThrow(() -> new BundleException("Cannot activate host " + bundle,
                                BundleException.ACTIVATOR_ERROR))
                        .sneakyForEachAndCollect(
                                bundle.adapt(BundleWiring.class)
                                        .getProvidedWires(HostNamespace.HOST_NAMESPACE)
                                        .stream()
                                        .map(BundleWire::getRequirer)
                                        .map(BundleRevision::getBundle),
                                bundle -> {
                                    bundle.adapt(Activation.class).install(Constants.EXTENSION_BUNDLE_ACTIVATOR);
                                })
                        .orElseThrow(
                                () -> new BundleException("Cannot activate fragments of " + bundle,
                                        BundleException.ACTIVATOR_ERROR));
            } catch (Throwable cause) {
                if (cause instanceof BundleException) {
                    throw cause;
                }
                if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new BundleException("Cannot activate " + bundle, BundleException.ACTIVATOR_ERROR, cause);
            } finally {
                activating = false;
            }
        }

        BundleWiring findHost(BundleWiring fragment) {
            return fragment
                    .getRequiredWires(HostNamespace.HOST_NAMESPACE)
                    .stream()
                    .findFirst()
                    .map(BundleWire::getProvider)
                    .map(BundleRevision::getWiring)
                    .map(this::findHost)
                    .orElse(fragment);
        }

        void uninstall() throws BundleException {
            if (activator == null) {
                return;
            }

            try {
                // deactivate fragments also
                TryCompanion.<Void> of(BundleException.class)
                        .sneakyForEachAndCollect(bundle
                                .adapt(BundleWiring.class)
                                .getProvidedWires(HostNamespace.HOST_NAMESPACE)
                                .stream()
                                .map(BundleWire::getRequirer)
                                .map(BundleRevision::getBundle),
                                bundle -> bundle.adapt(Activation.class).uninstall())
                        .sneakyRun(() -> activator
                                .stop(bundle
                                        .adapt(BundleWiring.class)
                                        .getRequiredWires(HostNamespace.HOST_NAMESPACE)
                                        .stream()
                                        .map(wire -> wire.getRequirer().getBundle())
                                        .findFirst()
                                        .orElseGet(() -> bundle)
                                        .getBundleContext()))
                        .orElseThrow(() -> new BundleException("Cannot stop " + bundle, BundleException.ACTIVATOR_ERROR,
                                null));
            } catch (Exception cause) {
                if (cause instanceof BundleException) {
                    throw cause;
                }
                if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new BundleException("Cannot stop " + bundle, BundleException.ACTIVATOR_ERROR, cause);
            } finally {
                byBundles.remove(bundle);
            }
        }

        @Override
        public String toString() { // formatter:off
            return new StringBuilder()
                    .append("Activation@")
                    .append(Integer.toHexString(hashCode()))
                    .append("[")
                    .append(bundle)
                    .append("]")
                    .toString();
        } // formatter:on
    }

    class DeferredActivation {

        final Bundle bundle;

        final String classname;

        DeferredActivation(Bundle bundle, String classname) {
            this.bundle = bundle;
            this.classname = classname;
        }

        @Override
        public String toString() {
            return "ClassLoading [bundle=" + bundle + ", classname=" + classname + "]";
        }

        void activate() throws BundleException {
            if ((bundle.getState() & (Bundle.STARTING | Bundle.ACTIVE)) != 0) {
                return;
            }
            bundle.adapt(OSGiLifecycle.Transitions.class).start(0);
        }
    }

    class LazyActivator {

        final Stack<DeferredActivation> loading = new Stack<>();

        final Stack<DeferredActivation> deferred = new Stack<>();

        void classloading(Bundle bundle, String classname) {
            loading.push(new DeferredActivation(bundle, classname));
        }

        void classloaded(Bundle bundle, String name) throws BundleException {
            deferred.push(loading.pop());
            if (!loading.isEmpty()) {
                return;
            }
            threadHolder.remove();
            BundleException errors = new BundleException("deferred activation " + bundle);
            while (!deferred.isEmpty()) {
                DeferredActivation activation = deferred.pop();
                try {
                    activation.activate();
                } catch (RuntimeException cause) {
                    errors.addSuppressed(cause);
                } catch (BundleException cause) {
                    errors.addSuppressed(cause);
                }
            }
            if (errors.getSuppressed().length > 0) {
                throw errors;
            }
        }

    }

    void classLoading(Bundle bundle, String classname) {
        threadHolder.get().classloading(bundle, classname);
    }

    void classLoaded(Bundle bundle, String classname) throws BundleException {
        threadHolder.get().classloaded(bundle, classname);
    }

    final BundleActivator NULL_ACTIVATOR = new NullActivator();

    class NullActivator implements BundleActivator {

        @Override
        public void start(BundleContext context) {
        }

        @Override
        public void stop(BundleContext context) {
        }

    }

}
