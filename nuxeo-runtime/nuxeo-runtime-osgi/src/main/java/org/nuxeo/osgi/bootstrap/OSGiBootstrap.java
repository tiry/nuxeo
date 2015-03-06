package org.nuxeo.osgi.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.nuxeo.common.LoaderConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

public class OSGiBootstrap implements FrameworkFactory {

    static {
        OSGiRuntimeBridge.install();
    }

    protected Set<String> bootPackages = new HashSet<>();

    protected Framework system;

    protected TransientContext context;

    protected OSGiClassLoader loader;

    public OSGiFile getFile() {
        return context.file;
    }

    public OSGiClassLoader loader() {
        return loader;
    }

    public void resetContext(OSGiClassLoader.Context context) throws BundleException {
        loader.context = context;
    }

    @Override
    public Framework newFramework(Map<String, String> env) {
        Properties config = new Properties(System.getProperties());
        config.putAll(env);
        try {
            return boot(config);
        } catch (BundleException cause) {
            throw new Error("Cannot boot in java", cause);
        }
    }

    public Set<String> bootPackages() {
        return Collections.unmodifiableSet(bootPackages);
    }

    public boolean matchBootPackages(String classpath) {
        return matchBootPackage(classpath.indexOf('.'), classpath);
    }

    boolean matchBootPackage(int indexof, String classpath) {
        if (indexof == -1) {
            return bootPackages.contains(classpath);
        }
        if (bootPackages.contains(classpath.substring(0, indexof))) {
            return true;
        }
        return matchBootPackage(classpath.indexOf('.', indexof + 1), classpath);
    }

    class TransientContext implements OSGiClassLoader.Context {

        TransientContext() throws BundleException {
            super();
        }

        protected final TransientFile file = new TransientFile();

        @Override
        public Class<?> findWiredClass(String name) throws IOException {
            String pathname = name.replace('.', '/')
                    .concat(".class");
            {
                Class<?> loaded = loader.findLoadedClass0(name);
                if (loaded != null) {
                    return loaded;
                }
            }
            Path path = file.getEntry(pathname);
            if (path == null) {
                return null;
            }
            return loader.defineClass(name, path);
        }

        @Override
        public URL getWiredResource(String pathname) {
            return OSGiFile.toURL.apply(file.getEntry(pathname));
        }

        @Override
        public Stream<URL> findWiredResources(String pathname) throws IOException {
            Path path = file.getEntry(pathname);
            List<URL> resources = Collections.emptyList();
            if (path != null) {
                resources = Collections.singletonList(path.toUri()
                        .toURL());
            }
            return resources.stream();
        }

        @Override
        public Bundle getBundle() {
            return system;
        }

    }

    protected Framework boot(Properties config) throws BundleException {
        context = new TransientContext();
        loader = new OSGiClassLoader(this, OSGiBootstrap.class.getClassLoader(), context);
        guessBootPackages(config);
        OSGiEnvironment env = new OSGiEnvironment(config);
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Framework> systemClass = (Class<? extends Framework>) loader
                    .loadClass("org.nuxeo.osgi.system.OSGiSystem");
            return system = systemClass.cast(MethodHandles.publicLookup()
                    .findConstructor(systemClass, MethodType.methodType(void.class, OSGiEnvironment.class))
                    .invoke(env));
        } catch (Throwable cause) {
            throw new BundleException("Cannot instantiate framework", cause);
        }
    }

    protected void guessBootPackages(Properties env) {
        bootPackages
                .addAll(Arrays.asList(new String[] { "META-INF", "java", "javax", "sun", "com.sun", "org.osgi", "javax",
                        "org.ietf.jgss", "org.omg", "org.w3c.dom", "org.xml.sax", "jdk",
                        "org.apache.xerces", "org.objectweb.asm", "org.nuxeo.common.trycompanion" }));
        String delegation = env.getProperty(LoaderConstants.OSGI_BOOT_DELEGATION, "");
        if (!delegation.isEmpty()) {
            bootPackages.addAll(Arrays.asList(delegation.split(",")));
        }
        bootPackages.add(OSGiBootstrap.class.getPackage()
                .getName());
    }

    public Path locate(Class<?> clazz) {
        class Util {
            final Class<?> typeof;

            Util(Class<?> typeof) {
                this.typeof = typeof;
            }

            int depthof() {
                String name = typeof.getName();
                int depth = 0;
                int index = name.indexOf('.');
                while (index > 0) {
                    depth += 1;
                    index = name.indexOf('.', index + 1);
                }
                return depth;
            }

            StringBuilder classname(StringBuilder builder, Class<?> clazz) {
                return Optional.ofNullable(clazz.getEnclosingClass())
                        .map(typeof -> classname(builder, typeof).append('$'))
                        .orElse(builder)
                        .append(clazz.getSimpleName());
            }

            Path rootpath() throws URISyntaxException {
                String name = classname(new StringBuilder(), typeof).append(".class")
                        .toString();
                URI uri = typeof.getResource(name)
                        .toURI();
                Path path = topath(uri);
                Path root = path.getRoot();
                int depth = depthof() + 1;
                if (path.getNameCount() > depth) {
                    path = path.subpath(0, path.getNameCount() - depth);
                    root = root.resolve(path);
                }
                return root;
            }

            Path topath(URI uri) {
                synchronized (OSGiBootstrap.class) {
                    try {
                        return Paths.get(uri);
                    } catch (FileSystemNotFoundException cause) {
                        try {
                            Map<String, String> env = new HashMap<>();
                            env.put("create", "true");
                            FileSystems.newFileSystem(uri, env);
                        } catch (IOException fserror) {
                            throw new UnsupportedOperationException("Cannot open " + uri, fserror);
                        }
                        return Paths.get(uri);
                    }
                }
            }
        }
        try {
            return new Util(clazz).rootpath();
        } catch (URISyntaxException cause) {
            throw new AssertionError("Cannot convert " + clazz.getName() + " to base dir", cause);
        }
    }

    public URI location(Path basepath) {
        URI location = basepath.toUri();
        if ("jar".equals(location.getScheme())) {
            String jarPath = location.getSchemeSpecificPart()
                    .split("!")[0];
            try {
                return new URI(jarPath);
            } catch (URISyntaxException cause) {
                new Error("Cannot handle " + location, cause);
            }
        } else if ("file".equals(location.getScheme())) {
            return basepath.toUri();
        }
        throw new UnsupportedOperationException("Cannot handle " + location);
    }

    class TransientFile implements OSGiFile {

        final Path basepath;

        final URI location;

        final Manifest mf;

        public TransientFile() throws BundleException {
            basepath = locate(OSGiBootstrap.class);
            location = location(basepath);
            mf = loadManifest(basepath);
        }

        @Override
        public URI getLocation() {
            return location;
        }

        @Override
        public Manifest getManifest() {
            return mf;
        }

        @Override
        public Path getEntry(String pathname) {
            if (pathname.startsWith("/")) {
                pathname = pathname.substring(1);
            }
            Path path = basepath.resolve(pathname);
            if (!Files.exists(path)) {
                return null;
            }
            return path;
        }

        @Override
        public DirectoryStream<Path> getEntryPaths(String pathname) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectoryStream<Path> findEntries(String pathname, String filter, boolean recurse) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(OSGiFile file) {
            throw new UnsupportedOperationException();
        }
    }

    BytesOutputStream fetch(Path path) throws IOException {
        try (InputStream input = path.toUri()
                .toURL()
                .openStream();
                BytesOutputStream output = new BytesOutputStream()) {
            byte[] buffer = new byte[4096];
            while (input.available() > 0) {
                output.write(buffer, 0, input.read(buffer, 0, 4096));
            }
            return output;
        }
    }

    class BytesOutputStream extends ByteArrayOutputStream {
        public BytesOutputStream() {
            super(8 * 4096);
        }

        @Override
        public synchronized byte[] toByteArray() {
            return buf;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> reloadClass(Class<T> clazz) throws ClassNotFoundException, BundleException {
        String location = location(locate(clazz)).toASCIIString();
        Bundle bundle = system.getBundleContext()
                .getBundle(location);
        bundle.start();
        return (Class<T>) bundle
                .adapt(ClassLoader.class)
                .loadClass(clazz.getName());
    }

}

class Streams {

    static <T> Stream<T> stream(Enumeration<T> e) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<T>() {
            @Override
            public T next() {
                return e.nextElement();
            }

            @Override
            public boolean hasNext() {
                return e.hasMoreElements();
            }
        }, Spliterator.ORDERED), false);
    }
}
