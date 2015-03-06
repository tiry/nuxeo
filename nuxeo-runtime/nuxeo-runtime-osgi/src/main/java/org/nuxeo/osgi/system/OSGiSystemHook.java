package org.nuxeo.osgi.system;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.jar.Manifest;

import org.nuxeo.osgi.bootstrap.OSGiFile;
import org.nuxeo.osgi.bootstrap.OSGiHook;
import org.nuxeo.osgi.internal.DictionaryBuilder;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.nuxeo.osgi.system.hook.OSGiDynamicHook;
import org.nuxeo.osgi.system.hook.OSGiHibernateHook;
import org.nuxeo.osgi.system.hook.OSGiLibraryHook;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

class OSGiSystemHook {

    final OSGiSystem system;

    Function<Bundle, OSGiHook> factory = bundle -> new IdentityHook();

    OSGiSystemHook(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<OSGiSystemHook>() {

            @Override
            public Class<OSGiSystemHook> typeof() {
                return OSGiSystemHook.class;
            }

            @Override
            public OSGiSystemHook adapt(Bundle bundle) {
                return OSGiSystemHook.this;
            }
        });
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<OSGiHook>() {

            @Override
            public Class<OSGiHook> typeof() {
                return OSGiHook.class;
            }

            @Override
            public OSGiHook adapt(Bundle bundle) {
                return factory.apply(bundle);
            }
        });
    }

    <T> T adapt(Class<T> typeof) {
        if (typeof.isAssignableFrom(BundleActivator.class)) {
            return typeof.cast(activator);
        }
        throw new UnsupportedOperationException();
    }

    final BundleActivator activator = new BundleActivator() {

        @Override
        public void start(BundleContext context) throws Exception {
            factory = bundle -> new ActiveHook(bundle);
            registerHook(context, new OSGiLibraryHook(), "library");
            registerHook(context, new OSGiDynamicHook(), "dynamic");
            registerHook(context, new OSGiHibernateHook(), "hibernate");
        }

        @Override
        public void stop(BundleContext context) throws Exception {
            factory = bundle -> new IdentityHook();
            unregisterHooks(context);
        }

        final Collection<ServiceRegistration<OSGiHook>> registrations = new LinkedList<>();

        void registerHook(BundleContext context, OSGiHook hook, String name) {
            registrations.add(context.registerService(OSGiHook.class, hook,
                    new DictionaryBuilder<String, String>().map("hook", name).build()));
        }

        void unregisterHooks(BundleContext context) {
            registrations.forEach(each -> each.unregister());
        }
    };

    class IdentityHook implements OSGiHook {

        @Override
        public OSGiFile onFile(OSGiFile file) throws BundleException {
            return file;
        }

        @Override
        public Manifest onManifest(OSGiFile file, Manifest mf) throws BundleException {
            return mf;
        }

    }

    class ActiveHook implements OSGiHook {

        final Bundle bundle;

        ActiveHook(Bundle bundle) {
            this.bundle = bundle;
        }

        @Override
        public Manifest onManifest(OSGiFile file, Manifest mf) throws BundleException {
            return apply(file, mf, (h, m) -> h.onManifest(file, m));
        }

        @Override
        public OSGiFile onFile(OSGiFile file) throws BundleException {
            return apply(file, file, (h, f) -> h.onFile(f));
        }

        <T, X extends Exception> T apply(OSGiFile file, T value, HookApplier<T, X> applier) throws X {
            BundleContext context = system.getBundleContext();
            for (ServiceReference<OSGiHook> ref : filterHooks(loadFilter(file))) {
                OSGiHook hook = context.getService(ref);
                value = applier.apply(hook, value);
                context.ungetService(ref);
            }
            return value;
        }

        Collection<ServiceReference<OSGiHook>> filterHooks(String filter) {
            try {
                return bundle.getBundleContext().getServiceReferences(OSGiHook.class, filter);
            } catch (InvalidSyntaxException cause) {
                throw new RuntimeException("Cannot fetch OSGi hooks", cause);
            }
        }

        String loadFilter(OSGiFile file) {
            Path path = file.getEntry("META-INF/OSGiHook.filter");
            if (path == null) {
                return "(hook=*)";
            }
            try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                return reader.readLine();
            } catch (IOException cause) {
                throw new AssertionError("Cannot load hooks filter from " + path, cause);
            }
        }
    }

    @FunctionalInterface
    interface HookApplier<T, X extends Throwable> {
        T apply(OSGiHook hook, T value) throws X;
    }
}
