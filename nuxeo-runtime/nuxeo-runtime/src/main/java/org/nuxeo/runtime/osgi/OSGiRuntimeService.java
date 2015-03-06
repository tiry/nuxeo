/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Bogdan Stefanescu
 *     Florent Guillaume
 *     Julien Carsique
 */

package org.nuxeo.runtime.osgi;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.common.LoaderConstants;
import org.nuxeo.common.trycompanion.Try;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.common.utils.ExceptionUtils;
import org.nuxeo.common.utils.TextTemplate;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.Version;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.nuxeo.runtime.model.RuntimeContext;
import org.nuxeo.runtime.model.impl.AbstractRuntimeService;
import org.nuxeo.runtime.model.impl.ComponentPersistence;
import org.nuxeo.runtime.model.impl.RegistrationInfoImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.namespace.extender.ExtenderNamespace;

/**
 * The default implementation of NXRuntime over an OSGi compatible environment.
 *
 * @author Bogdan Stefanescu
 * @author Florent Guillaume
 */
public class OSGiRuntimeService extends AbstractRuntimeService implements FrameworkListener {

    public static final ComponentName FRAMEWORK_STARTED_COMP = new ComponentName("org.nuxeo.runtime.started");

    /** Can be used to change the runtime home directory */
    public static final String PROP_HOME_DIR = "org.nuxeo.runtime.home";

    /** The OSGi application install directory. */
    public static final String PROP_INSTALL_DIR = "INSTALL_DIR";

    /** The OSGi application config directory. */
    public static final String PROP_CONFIG_DIR = "CONFIG_DIR";

    /** The host adapter. */
    public static final String PROP_HOST_ADAPTER = "HOST_ADAPTER";

    public static final String PROP_NUXEO_BIND_ADDRESS = "nuxeo.bind.address";

    public static final String NAME = "OSGi NXRuntime";

    public static final Version VERSION = Version.parseString("1.4.0");

    private final Log log = LogFactory.getLog(OSGiRuntimeService.class);

    protected final BundleContext bundleContext;

    protected boolean appStarted = false;

    protected final String name;

    /**
     * OSGi doesn't provide a method to lookup bundles by symbolic name. This table is used to map symbolic names to
     * bundles. This map is not handling bundle versions.
     */
    protected final Map<String, Bundle> bundlesByName = new ConcurrentHashMap<String, Bundle>();

    protected final ComponentPersistence persistence;

    protected static Map<String, String> toMap(Dictionary<?, ?> dict) {
        if (dict == null) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<String, String>();
        Enumeration<?> keys = dict.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            map.put(key.toString(), dict.get(key).toString());
        }
        return map;
    }

    public OSGiRuntimeService(BundleContext context) {
        this(new OSGiRuntimeContext(context.getBundle()), context);
    }

    public OSGiRuntimeService(OSGiRuntimeContext runtimeContext, BundleContext context) {
        super(runtimeContext, Environment.getDefault().getProperties());
        runtimeContext.register(this);
        bundleContext = context;
        name = getProperty(LoaderConstants.APP_NAME, NAME);
        setProperty(OSGiRuntimeService.PROP_HOME_DIR,
                getProperty(OSGiRuntimeService.PROP_HOME_DIR, getProperty(LoaderConstants.HOME_DIR)));
        URL configLocation = context.getBundle().getResource("/OSGI-INF/nuxeo.properties");
        if (configLocation != null) {
            setProperty(OSGiRuntimeService.PROP_CONFIG_DIR, configLocation.toExternalForm());
        }
        workingDir = Environment.getDefault().getHome();
        log.debug("Home directory: " + workingDir);
        workingDir.mkdirs();
        persistence = new ComponentPersistence(this);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Version getVersion() {
        return VERSION;
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    @Override
    public Bundle getBundle(String symbolicName) {
        return bundlesByName.get(symbolicName);
    }

    public Map<String, Bundle> getBundlesMap() {
        return bundlesByName;
    }

    public ComponentPersistence getComponentPersistence() {
        return persistence;
    }

    public OSGiRuntimeContext getOrCreateContext(Bundle bundle) throws RuntimeServiceException {
        OSGiRuntimeContext ctx = getContext(bundle);
        if (ctx != null) {
            return ctx;
        }
        if (bundle.equals(runtimeContext.getBundle())) {
            ctx = (OSGiRuntimeContext) runtimeContext;
            if (ctx.isActivated()) {
                return ctx;
            }
        } else {
            ctx = new OSGiRuntimeContext(bundle);
        }
        ctx.register(this);
        return ctx;
    }

    public OSGiRuntimeContext getContext(Bundle bundle) {
        return (OSGiRuntimeContext) getContext(bundle.getSymbolicName());
    }

    @Override
    protected void doStart() {
        super.doStart();
        runtimeContext.activate();
        bundleContext.addFrameworkListener(this);
        try {
            loadConfig();
        } catch (IOException | BundleException cause) {
            throw new RuntimeException("Cannot load configuration", cause);
        } // load configuration if any
    }

    @Override
    protected void doStop() {
        bundleContext.removeFrameworkListener(this);
        super.doStop();
        runtimeContext.destroy();
    }

    @Override
    public void reloadProperties() {
        super.reloadProperties();
        try {
            loadConfig();
        } catch (IOException | BundleException cause) {
            throw new RuntimeServiceException("Cannot reload config", cause);
        }
    }

    // TODO use a OSGi service for this.
    protected void loadConfigurationFromProvider(RuntimeContext context, Iterable<URL> provider) throws IOException {
        List<URL> blacklists = new ArrayList<URL>();
        List<URL> props = new ArrayList<URL>();
        List<URL> xmls = new ArrayList<URL>();
        for (URL url : provider) {
            String path = url.getPath();
            if (path.endsWith(".xml")) {
                xmls.add(url);
            } else if (path.endsWith(".properties")) {
                props.add(url);
            } else if (path.endsWith("blacklist")) {
                blacklists.add(url);
            }
        }
        Comparator<URL> comp = new Comparator<URL>() {
            @Override
            public int compare(URL o1, URL o2) {
                return o1.getPath().compareTo(o2.getPath());
            }
        };
        loadBlacklists(blacklists, comp);
        loadProperties(props, comp);
        loadComponents(context, xmls, comp);
    }

    protected void loadProperties(List<URL> props, Comparator<URL> comp) throws IOException {
        Collections.sort(props, comp);
        for (URL url : props) {
            loadProperties(url.openStream());
        }
    }

    protected void loadComponents(RuntimeContext context, List<URL> urls, Comparator<URL> comp) {
        Collections.sort(urls, comp);
        for (URL url : urls) {
            try {
                context.deploy(url);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot load config from " + url, e);
            }
        }
    }

    protected void loadBlacklists(List<URL> blacklists, Comparator<URL> comp) {
        Function<URL, BufferedReader> opener = url -> {
            try {
                return new BufferedReader(new InputStreamReader(url.openStream()));
            } catch (IOException cause) {
                return null;
            }
        };
        manager.setBlacklist(blacklists.stream()
                .sorted(comp)
                .map(opener)
                .filter(reader -> reader != null)
                .flatMap(reader -> reader.lines())
                .collect(Collectors.toSet()));
    }

    protected void loadConfig() throws IOException, BundleException {
        Environment env = Environment.getDefault();
        log.info("Configuration: host application: " + env.getHostApplicationName());

        Iterable<URL> provider = Environment.getDefault().getConfigurationProvider();
        if (provider != null) {
            loadConfigurationFromProvider(runtimeContext, provider);
        }
    }

    protected void printDeploymentOrderInfo(String[] fileNames) {
        if (log.isDebugEnabled()) {
            StringBuilder buf = new StringBuilder();
            for (String fileName : fileNames) {
                buf.append("\n\t" + fileName);
            }
            log.debug("Deployment order of configuration files: " + buf.toString());
        }
    }

    /**
     * Overrides the default method to be able to include OSGi properties.
     */
    @Override
    public String getProperty(String name, String defValue) {
        String value = properties.getProperty(name);
        if (value == null) {
            value = bundleContext.getProperty(name);
            if (value == null) {
                return defValue == null ? null : expandVars(defValue);
            }
        }
        if (("${" + name + "}").equals(value)) {
            // avoid loop, don't expand
            return value;
        }
        return expandVars(value);
    }

    /**
     * Overrides the default method to be able to include OSGi properties.
     */
    @Override
    public String expandVars(String expression) {
        return new TextTemplate(getProperties()) {
            @Override
            public String getVariable(String name) {
                String value = super.getVariable(name);
                if (value == null) {
                    value = bundleContext.getProperty(name);
                }
                return value;
            }

        }.processText(expression);
    }

    protected void notifyComponentsOnStarted() throws RuntimeServiceException {
        Map<Integer, Set<RegistrationInfo>> index = new TreeMap<>();
        manager.getRegistrations().stream().forEach(ri -> {
            Integer level = Integer.valueOf(ri.getApplicationStartedOrder());
            if (!index.containsKey(level)) {
                index.put(level, new HashSet<RegistrationInfo>());
            }
            index.get(level).add(ri);
        });
        for (Integer level : index.keySet()) {
            try {
                TryCompanion.<Void> of(RuntimeServiceException.class)
                        .forEachAndCollect(index.get(level).stream(), RegistrationInfo::notifyApplicationStarted)
                        .orElseThrow(() -> new RuntimeServiceException("Caught errors at level " + level));
            } catch (RuntimeServiceException errors) {
                log.error(errors.getMessage(), errors);
            }
        }
    }

    protected static class CompoundEnumerationBuilder {

        protected final ArrayList<Enumeration<URL>> collected = new ArrayList<Enumeration<URL>>();

        public CompoundEnumerationBuilder add(Enumeration<URL> e) {
            collected.add(e);
            return this;
        }

        public Enumeration<URL> build() {
            return new CompoundEnumeration<URL>(collected);
        }

    }

    protected static class CompoundEnumeration<E> implements Enumeration<E> {

        protected final Iterator<Enumeration<E>> enums;

        @SafeVarargs
        public CompoundEnumeration(Enumeration<E>... enums) {
            this(new Iterator<Enumeration<E>>() {

                int index = 0;

                @Override
                public boolean hasNext() {
                    return index < enums.length;
                }

                @Override
                public Enumeration<E> next() {
                    if (index >= enums.length) {
                        throw new NoSuchElementException();
                    }
                    return enums[index++];
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }

            });
        }

        public CompoundEnumeration(Iterable<Enumeration<E>> enums) {
            this(enums.iterator());
        }

        public CompoundEnumeration(Iterator<Enumeration<E>> enums) {
            this.enums = enums;
        }

        Enumeration<E> current;

        @Override
        public boolean hasMoreElements() {
            if (current != null && current.hasMoreElements()) {
                return true;
            }
            while (enums.hasNext()) {
                current = enums.next();
                if (current.hasMoreElements()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public E nextElement() {
            return current.nextElement();
        }

    }

    protected static class BundleConfigurationProvider implements Iterable<URL> {
        protected final Bundle bundle;

        protected BundleConfigurationProvider(Bundle bundle) {
            this.bundle = bundle;
        }

        @Override
        public Iterator<URL> iterator() {
            CompoundEnumerationBuilder builder = new CompoundEnumerationBuilder();
            builder.add(bundle.findEntries("/", "*.properties", true));
            builder.add(bundle.findEntries("/", "*-config.xml", true));
            builder.add(bundle.findEntries("/", "*-bundle.xml", true));
            final Enumeration<URL> entries = builder.build();
            return new Iterator<URL>() {

                @Override
                public boolean hasNext() {
                    return entries.hasMoreElements();
                }

                @Override
                public URL next() {
                    return entries.nextElement();
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }

            };

        }
    }

    protected static class RIApplicationStartedComparator implements Comparator<RegistrationInfo> {
        @Override
        public int compare(RegistrationInfo r1, RegistrationInfo r2) {
            return r1.getApplicationStartedOrder() - r2.getApplicationStartedOrder();
        }
    }

    public void fireApplicationStarted() throws RuntimeServiceException {
        TryCompanion.<Void> of(RuntimeServiceException.class)
                .empty()
                .consume(self -> handleApplicationStarted(self))
                .run(() -> notifyComponentsOnStarted())
                .peek(self -> printStatusMessage())
                .orElseThrow(() -> new RuntimeServiceException("Caught errors while handling application started"));
    }

    protected Try<Void> handleApplicationStarted(Try<Void> companion) {
        if (appStarted) {
            return companion;
        }
        appStarted = true;
        return companion
                .run(() -> persistence.loadPersistedComponents())
                .run(() -> deployFrameworkStartedComponent());

    }

    /* --------------- FrameworkListener API ------------------ */

    int startlevel = 0;

    @Override
    public void frameworkEvent(FrameworkEvent event) {
        int type = event.getType();
        if (type == FrameworkEvent.STARTED) {
            fireApplicationStarted();
            startlevel = bundleContext.getBundle().adapt(FrameworkStartLevel.class).getStartLevel();
        } else if (type == FrameworkEvent.STARTLEVEL_CHANGED) {
            int newlevel = bundleContext.getBundle().adapt(FrameworkStartLevel.class).getStartLevel();
            if (newlevel > startlevel) {
                startlevel = newlevel;
                fireApplicationStarted();
            }
        }
    }

    private void printStatusMessage() {
        StringBuilder msg = new StringBuilder();
        msg.append("Nuxeo Platform Started\n");
        if (getStatusMessage(msg)) {
            log.info(msg);
        } else {
            log.error(msg);
        }
   }

    protected void deployFrameworkStartedComponent() throws RuntimeServiceException {
        RegistrationInfoImpl ri = new RegistrationInfoImpl(FRAMEWORK_STARTED_COMP);
        ri.setContext(runtimeContext);
        // this will register any pending components that waits for the
        // framework to be started
        manager.register(ri);
    }

    public Bundle findHostBundle(Bundle bundle) {
        String hostId = bundle.getHeaders().get(Constants.FRAGMENT_HOST);
        if (hostId == null) {
            return bundle;
        }
        int p = hostId.indexOf(';');
        if (p > -1) { // remove version or other extra information if any
            hostId = hostId.substring(0, p);
        }
        return bundlesByName.get(hostId);
    }

    protected File getEclipseBundleFileUsingReflection(Bundle bundle) {
        try {
            Object proxy = bundle.getClass().getMethod("getLoaderProxy").invoke(bundle);
            Object loader = proxy.getClass().getMethod("getBundleLoader").invoke(proxy);
            URL root = (URL) loader.getClass().getMethod("findResource", String.class).invoke(loader, "/");
            Field field = root.getClass().getDeclaredField("handler");
            field.setAccessible(true);
            Object handler = field.get(root);
            Field entryField = handler.getClass().getSuperclass().getDeclaredField("bundleEntry");
            entryField.setAccessible(true);
            Object entry = entryField.get(handler);
            Field fileField = entry.getClass().getDeclaredField("file");
            fileField.setAccessible(true);
            return (File) fileField.get(entry);
        } catch (Exception cause) {
            ExceptionUtils.checkInterrupt(cause);
            throw new RuntimeServiceException(
                    "Cannot access to eclipse bundle system files of " + bundle.getSymbolicName(), cause);
        }
    }

    @Override
    public File getBundleFile(Bundle bundle) {
        String vendor = Framework.getProperty(Constants.FRAMEWORK_VENDOR);
        if ("Eclipse".equals(vendor)) { // equinox framework
            return getEclipseBundleFileUsingReflection(bundle);
        }
        String location = bundle.getLocation();
        try {
            URL url = new URL(bundle.getLocation());
            String scheme = url.getProtocol();
            if ("file".equals(scheme)) {
                return Paths.get(url.toURI()).toFile();
            }
            if ("jar".equals(scheme)) {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                return new File(connection.getJarFileURL().toURI());
            }
        } catch (URISyntaxException | IOException cause) {
            throw new RuntimeServiceException("Cannot handle " + location, cause);
        }

        throw new RuntimeServiceException("Unknown " + location + ", cannot locate file");
    }

    public static final boolean isJBoss4(Environment env) {
        if (env == null) {
            return false;
        }
        String hn = env.getHostApplicationName();
        String hv = env.getHostApplicationVersion();
        if (hn == null || hv == null) {
            return false;
        }
        return "JBoss".equals(hn) && hv.startsWith("4");
    }

    protected void addWarning(String message) {
        warnings.add(message);
    }

    public OSGiRuntimeContext installBundle(Bundle bundle) throws RuntimeServiceException {
        if (!bundle.adapt(BundleWiring.class)
                .getRequiredWires(ExtenderNamespace.EXTENDER_NAMESPACE)
                .stream()
                .anyMatch(wire -> wire.getProvider().getBundle() == getContext().getBundle())) {
            return null;
        }
        bundlesByName.put(bundle.getSymbolicName(), bundle);
        return getOrCreateContext(bundle);
    }

}
