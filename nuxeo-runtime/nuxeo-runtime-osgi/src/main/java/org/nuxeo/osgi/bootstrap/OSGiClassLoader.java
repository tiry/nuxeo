package org.nuxeo.osgi.bootstrap;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;


public class OSGiClassLoader extends ClassLoader implements BundleReference {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    public interface Context extends BundleReference {

        /**
         * Find class in wired bundles. Returns null if the class is not found.
         */
        Class<?> findWiredClass(String classname) throws BundleException, IOException;

        /**
         * Get resource in wired bundles. Returns null is the resource is not found.
         */
        URL getWiredResource(String pathname) throws BundleException;

        /**
         * Search all resources in wired bundles.
         */
        Stream<URL> findWiredResources(String pathname) throws BundleException, IOException;

    }

    final OSGiBootstrap bootstrap;

    Context context;

    public OSGiClassLoader(OSGiBootstrap bootstrap, ClassLoader parent, Context context) {
        super(parent);
        this.bootstrap = bootstrap;
        this.context = context;
    }

    public Class<?> findLoadedClass0(String classname) {
        synchronized (getClassLoadingLock(classname)) {
            return findLoadedClass(classname);
        }
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = findBootClass(name);
        if (clazz == null) {
            try {
                clazz = context.findWiredClass(name);
            } catch (BundleException | IOException cause) {
                throw new ClassNotFoundException("Cannot load " + name + " in " + this, cause);
            }
        }
        if (clazz == null) {
            throw new ClassNotFoundException("Cannot load " + name + " in " + this);
        }
        if (true) {
            resolveClass(clazz);
        }
        return clazz;
    }

    @Override
    public URL getResource(String name) {
        URL url = getBootResource(name);
        if (url != null) {
            return url;
        }
        try {
            return context.getWiredResource(name);
        } catch (BundleException cause) {
            throw new RuntimeException("Cannot find " + name + " in " + this, cause);
        }
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL>[] tmp = (Enumeration<URL>[]) new Enumeration<?>[2];
        tmp[0] = getBootResources(name);
        tmp[1] = findResources(name);
        return new CompoundEnumeration<>(tmp);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return context.findWiredClass(name);
        } catch (BundleException | IOException cause) {
            throw new ClassNotFoundException("Cannot find " + name + " in " + this, cause);
        }
    }

    @Override
    protected Enumeration<URL> findResources(final String path) throws IOException {
        try {
            return toEnum(context.findWiredResources(path).iterator());
        } catch (BundleException cause) {
            throw new IOException("Cannot find " + path + " in " + this, cause);
        }
    }

    public Class<?> defineClass(String name, Path path) throws IOException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> clazz = findLoadedClass(name);
            if (clazz != null) {
                return clazz;
            }
            definePackage(name);
            SeekableByteChannel channel = Files.newByteChannel(path);
            try {
                ByteBuffer buffer = ByteBuffer.allocateDirect((int) channel.size());
                channel.read(buffer);
                buffer.flip();
                return defineClass(name, buffer, null);
            } finally {
                channel.close();
            }
        }
    }

    protected void definePackage(String classname) {
        int i = classname.lastIndexOf('.');
        if (i == -1) {
            return;
        }
        String pkgname = classname.substring(0, i);
        if (getPackage(pkgname) != null) {
            return;
        }
        definePackage(pkgname, null, null, null, null, null, null, null);
    }

    @Override
    public String toString() {
        return "OSGiClassLoader@" + Integer.toHexString(hashCode()) + "[" + context.getBundle() + "]";
    }

    public URL getBootResource(String pathname) {
        // if (!bootstrap.matchBootPackages(pathname.replace('/', '.'))) {
        // return null;
        // }
        return bootstrap.loader.getParent().getResource(pathname);
    }

    public Enumeration<URL> getBootResources(String pathname) throws IOException {
        if (!bootstrap.matchBootPackages(pathname.replace('/', '.'))) {
            return Collections.emptyEnumeration();
        }
        return bootstrap.loader.getParent().getResources(pathname);
    }

    public Class<?> findBootClass(String classname) {
        if (!bootstrap.matchBootPackages(classname)) {
            return null;
        }
        try {
            return bootstrap.loader.getParent().loadClass(classname);
        } catch (ClassNotFoundException cause) {
            return null;
        }
    }

    public OSGiBootstrap getBootstrap() {
        return bootstrap;
    }

    @Override
    public Bundle getBundle() {
        return context.getBundle();
    }

    public void close() {
        ;
    }

    static <T> Enumeration<T> toEnum(Iterator<T> it) {
        return new Enumeration<T>() {

            @Override
            public boolean hasMoreElements() {
                return it.hasNext();
            }

            @Override
            public T nextElement() {
                return it.next();
            }

        };
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


}
