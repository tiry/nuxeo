/*******************************************************************************
 * Copyright (c) 2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.nuxeo.osgi.system;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.nuxeo.common.trycompanion.SneakyThrow.ConsumerCheckException;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Resource;

public class OSGiClasspath {

    final OSGiSystem system;

    OSGiClasspath(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<OSGiClasspath>() {

            @Override
            public Class<OSGiClasspath> typeof() {
                return OSGiClasspath.class;
            }

            @Override
            public OSGiClasspath adapt(Bundle bundle) {
                return OSGiClasspath.this;
            }

        });
    }

    Stream<String> scan() throws BundleException {
        return scanSurefireClasspath(scanLoader());
    }

    Stream<String> scanLoader() throws BundleException {
        ClassLoader loader = system.bootstrap.loader().getParent();
        if (loader instanceof URLClassLoader) {
            return scanURLClassloader((URLClassLoader) loader);
        } else if (loader.getClass().getName().equals("org.apache.tools.ant.AntClassLoader")) {
            return scanAntClassloader(loader);
        } else {
            throw new BundleException("Unknown classloader type: " + loader.getClass().getName()
                    + "\nWon't be able to load OSGI bundles");
        }
    }

    Stream<String> scanSurefireClasspath(Stream<String> stream) throws BundleException {
        List<String> entries = stream.collect(Collectors.toList());
        for (String location : entries) {
            if (location.matches(".*/nuxeo-runtime-osgi[^/]*\\.jar")) {
                break;
            }
            if (location.startsWith("file:") && location.contains("surefirebooter")) {
                try {
                    return loadSurefireClasspath(location);
                } catch (IOException cause) {
                    throw new BundleException("Cannot load surefire classpath from " + location, cause);
                }
            }
        }
        return entries.stream();
    }

    Stream<String> loadSurefireClasspath(String location) throws IOException {
        try (JarFile surefirebooterJar = new JarFile(Paths.get(URI.create(location)).toFile())) {
            String cp = surefirebooterJar.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            return Arrays.stream(cp.split(" ")).map(path -> path.startsWith("file:") ? path : "file:" + path);
        }
    }

    Stream<String> scanAntClassloader(ClassLoader loader) {
        String cp;
        try {
            Method method = loader.getClass().getMethod("getClasspath");
            cp = (String) method.invoke(loader);
        } catch (ReflectiveOperationException cause) {
            throw new AssertionError("Cannot scan ant classpath", cause);
        }
        return Arrays.stream(cp.split(File.pathSeparator)).map(path -> "file:".concat(path));
    }

    Stream<String> scanURLClassloader(URLClassLoader loader) {
        return Arrays.stream(loader.getURLs()).map(url -> url.toExternalForm());
    }

    final BundleActivator activator = new BundleActivator() {

        @Override
        public void start(BundleContext context) throws Exception {
            TryCompanion.<Void> of(BundleException.class)
                    .sneakyConsume(self -> installBundles(self))
                    .sneakyConsume(self -> resolveSystemBundles(self.companion()))
                    .orElseThrow(() -> new BundleException("Caught errors while starting system"));
        }

        @Override
        public void stop(BundleContext context) throws Exception {
            TryCompanion.<Void> of(BundleException.class)
                    .sneakyConsume(self -> stopBundles(self))
                    .sneakyConsume(self -> uninstallBundles(self.companion()));
        }

        void installBundles(TryCompanion<Void> self) throws BundleException {
            self
                    .sneakyForEachAndCollect(
                            scan(),
                            (ConsumerCheckException<String>) location -> system.installBundle(location))
                    .orElseThrow(() -> new BundleException("Caught errors while installing bundles"));
        }

        void uninstallBundles(TryCompanion<Void> context) throws BundleException {
            context
                    .sneakyForEachAndCollect(
                            Stream.of(system.getBundleContext().getBundles())
                                    .filter(bundle -> bundle == system.bundle),
                            bundle -> bundle.uninstall())
                    .orElseThrow(() -> new BundleException("Caught errors while uninstalling bundles"));
        }

        void resolveSystemBundles(TryCompanion<Void> context) throws BundleException {
            BundleRevision systemRevision = system.adapt(BundleRevision.class);
            // resolve system first
            resolve(
                    context,
                    Collections.singletonList(systemRevision),
                    Collections.emptyList());
        }

        void resolve(TryCompanion<Void> monitor, Collection<Resource> mandatories, Collection<Resource> optionals)
                throws BundleException {
            system.adapt(OSGiWiring.Admin.class)
                    .resolve(mandatories, optionals)
                    .orElseThrow(() -> new BundleException("Cannot resolve", BundleException.RESOLVE_ERROR, null));
        }

        void stopBundles(TryCompanion<Void> context) throws BundleException {
            system.stop();
        }

    };

    <T> T adapt(Class<T> type) {
        return type.cast(activator);
    }

}
