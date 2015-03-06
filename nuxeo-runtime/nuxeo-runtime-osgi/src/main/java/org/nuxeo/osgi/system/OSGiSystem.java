package org.nuxeo.osgi.system;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.nuxeo.osgi.bootstrap.OSGiBootstrap;
import org.nuxeo.osgi.bootstrap.OSGiClassLoader;
import org.nuxeo.osgi.bootstrap.OSGiEnvironment;
import org.nuxeo.osgi.bootstrap.OSGiFile;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.startlevel.FrameworkStartLevel;

public class OSGiSystem implements Framework {

    final OSGiEnvironment environment;

    final OSGiBootstrap bootstrap = fetchBootstrap();

    final OSGiBundle bundle = new OSGiBundle(this);

    final OSGiBundleAdapter systemAdapter = new OSGiBundleAdapter(this);

    final OSGiSystemHook systemhook = new OSGiSystemHook(this);

    final OSGiFilesystem filesystem = new OSGiFilesystem(this);

    final OSGiRepository registry = new OSGiRepository(this);

    final OSGiLifecycle lifecycle = new OSGiLifecycle(this);

    final OSGiResolver resolver = new OSGiResolver(this);

    final OSGiWiring wiring = new OSGiWiring(this);

    final OSGiLoader loader = new OSGiLoader(this);

    final OSGiActivator activator = new OSGiActivator(this);

    final OSGiExecutor executor = new OSGiExecutor(this);

    final OSGiStartLevel startlevel = new OSGiStartLevel(this);

    final OSGiService service = new OSGiService(this);

    final OSGiEventRelayer eventrelayer = new OSGiEventRelayer(this);

    final OSGiDataFile datafile = new OSGiDataFile(this);

    final OSGiLogger logger = new OSGiLogger(this);

    final OSGiConfigurator configurator = new OSGiConfigurator(this);

    final OSGiURLHandlers urlhandlers = new OSGiURLHandlers(this);

    final OSGiLogin login = new OSGiLogin(this);

    final OSGiClasspath classpath = new OSGiClasspath(this);

    public OSGiSystem(OSGiEnvironment env) throws BundleException, IOException, ParseException {
        environment = env;
        installAdapters(env);
        init();
    }

    void installAdapters(OSGiEnvironment env) {
        adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<Properties>() {

            @Override
            public Class<Properties> typeof() {
                return Properties.class;
            }

            @Override
            public Properties adapt(Bundle bundle) {
                return environment;
            }
        });
        adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<OSGiEnvironment>() {

            @Override
            public Class<OSGiEnvironment> typeof() {
                return OSGiEnvironment.class;
            }

            @Override
            public OSGiEnvironment adapt(Bundle bundle) {
                return env;
            }
        });
    }

    OSGiBootstrap fetchBootstrap() {
        return ((OSGiClassLoader) OSGiSystem.class.getClassLoader()).getBootstrap();
    }

    OSGiBundle installBundle(String location) throws BundleException {
        final URI uri = URI.create(location);
        final OSGiFile file = bundle.adapt(OSGiFilesystem.Activation.class).adapt(uri);
        return installBundle(file);
    }

    OSGiBundle installBundle(OSGiFile file) throws BundleException {
        if (file == bundle.adapt(OSGiFile.class)) {
            bundle.adapt(OSGiFilesystem.Activation.class).update(file.getLocation());
            return bundle; // don't re-install system
        }
        final OSGiBundle actual = new OSGiBundle(this);
        actual.adapt(OSGiFilesystem.Activation.class).install(file);
        actual.adapt(OSGiLifecycle.Transitions.class).install();
        return actual;
    }

    public static class Activator implements BundleActivator {

        @Override
        public void start(BundleContext context) throws Exception {
            final Bundle bundle = context.getBundle();
            bundle.adapt(OSGiSystemHook.class).adapt(BundleActivator.class).start(context);
            bundle.adapt(OSGiRepository.class).adapt(BundleActivator.class).start(context);
            bundle.adapt(OSGiResolver.class).adapt(BundleActivator.class).start(context);
            bundle.adapt(OSGiWiring.class).adapt(BundleActivator.class).start(context);
            bundle.adapt(OSGiExecutor.class).adapt(BundleActivator.class).start(context);
            bundle.adapt(OSGiConfigurator.class).adapt(BundleActivator.class).start(context);
            bundle.adapt(OSGiURLHandlers.class).adapt(BundleActivator.class).start(context);
            bundle.adapt(OSGiLogger.class).adapt(BundleActivator.class).start(context);
            bundle.adapt(OSGiLogin.class).adapt(BundleActivator.class).start(context);
            bundle.adapt(OSGiEventRelayer.class).adapt(BundleActivator.class).start(context);
            bundle.adapt(OSGiClasspath.class).adapt(BundleActivator.class).start(context);
        }

        @Override
        public void stop(BundleContext context) throws Exception {
            final Bundle bundle = context.getBundle();

            bundle.adapt(OSGiClasspath.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiWiring.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiResolver.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiRepository.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiSystemHook.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiExecutor.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiConfigurator.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiURLHandlers.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiLogger.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiLogin.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiEventRelayer.class).adapt(BundleActivator.class).stop(context);
            bundle.adapt(OSGiSystemHook.class).adapt(BundleActivator.class).stop(context);
        }

    }

    @Override
    public void init() throws BundleException {
        init(event -> {
        });
    }

    @Override
    public void init(FrameworkListener... listeners) throws BundleException {
        bundle.adapt(OSGiFilesystem.Activation.class).install(bootstrap.getFile());
        bundle.adapt(OSGiLifecycle.Transitions.class).install();
        start();
        bundle.adapt(FrameworkStartLevel.class).setStartLevel(0, listeners);
    }

    @Override
    public FrameworkEvent waitForStop(long timeout) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T adapt(Class<T> type) {
        return bundle.adapt(type);
    }

    <T> T adapt(Class<T> type, Bundle bundle) throws BundleException {
        if (type.isAssignableFrom(Framework.class)) {
            return type.cast(this);
        }
        if (type.isAssignableFrom(OSGiSystem.class)) {
            return type.cast(this);
        }
        if (type.isAssignableFrom(OSGiBootstrap.class)) {
            return type.cast(bootstrap);
        }
        @SuppressWarnings("unchecked")
        final BundleAdapter<T> adapter = (BundleAdapter<T>) systemAdapter.byTypes.get(type);
        if (adapter == null) {
            throw new UnsupportedOperationException("Cannot adapt " + this + " to " + type);
        }
        final T adapt = adapter.adapt(bundle);
        if (adapt == null) {
            throw new NullPointerException("Cannot adapt " + this + " to " + type);
        }
        return adapt;
    }

    @Override
    public void start() throws BundleException {
        start(Bundle.START_ACTIVATION_POLICY);
    }

    @Override
    public int compareTo(Bundle o) {
        if (o instanceof OSGiSystem) {
            return 0;
        }
        return Long.compare(getBundleId(), o.getBundleId());
    }

    @Override
    public int getState() {
        return bundle.getState();
    }

    @Override
    public void start(int options) throws BundleException {
        try {
            adapt(OSGiLifecycle.Transitions.class).start(options);
        } catch (final BundleException cause) {
            bundle.adapt(FrameworkListener.class)
                    .frameworkEvent(new FrameworkEvent(FrameworkEvent.ERROR, bundle, cause));
            throw cause;
        }
        bundle.adapt(FrameworkListener.class).frameworkEvent(new FrameworkEvent(FrameworkEvent.STARTED, bundle, null));
    }

    @Override
    public void stop(int options) throws BundleException {
        adapt(OSGiLifecycle.Transitions.class).stop(options);
    }

    @Override
    public void stop() throws BundleException {
        stop(0);
    }

    @Override
    public void update(InputStream input) throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update() throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void uninstall() throws BundleException {
        adapt(OSGiLifecycle.Transitions.class).uninstall();
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return bundle.getHeaders();
    }

    @Override
    public long getBundleId() {
        return 0L;
    }

    @Override
    public String getLocation() {
        return bundle.getLocation();
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        return bundle.getRegisteredServices();
    }

    @Override
    public ServiceReference<?>[] getServicesInUse() {
        return bundle.getServicesInUse();
    }

    @Override
    public boolean hasPermission(Object permission) {
        return bundle.hasPermission(permission);
    }

    @Override
    public URL getResource(String pathname) {
        return bundle.getResource(pathname);
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        return bundle.getHeaders(locale);
    }

    @Override
    public String getSymbolicName() {
        return bundle.getSymbolicName();
    }

    @Override
    public Class<?> loadClass(String classname) throws ClassNotFoundException {
        return adapt(OSGiClassLoader.class).loadClass(classname);
    }

    @Override
    public Enumeration<URL> getResources(String pathname) throws IOException {
        return bundle.getResources(pathname);
    }

    @Override
    public Enumeration<String> getEntryPaths(String pathname) {
        return bundle.getEntryPaths(pathname);
    }

    @Override
    public URL getEntry(String pathname) {
        return bundle.getEntry(pathname);
    }

    @Override
    public long getLastModified() {
        return bundle.getLastModified();

    }

    @Override
    public Enumeration<URL> findEntries(String pathname, String filePattern, boolean recurse) {
        return bundle.findEntries(pathname, filePattern, recurse);
    }

    @Override
    public BundleContext getBundleContext() {
        return bundle.getBundleContext();
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
        return bundle.getSignerCertificates(signersType);
    }

    @Override
    public Version getVersion() {
        return bundle.getVersion();
    }

    @Override
    public File getDataFile(String filename) {
        return bundle.getDataFile(filename);
    }

    @Override
    public String toString() {
        return "OSGiSystem@" + Integer.toHexString(hashCode());
    }

}
