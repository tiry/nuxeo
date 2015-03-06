/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     bstefanescu, jcarsique
 */
package org.nuxeo.osgi.bootstrap.application;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.nuxeo.common.LoaderConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class OSGiFrameworkLoader implements LoaderConstants {

    private static OSGiFrameworkLoader loader;

    protected final File[] extensionFiles;

    protected final File[] bundleFiles;

    protected final File[] libraryFiles;

    protected final OSGiAdapter osgi;

    public OSGiFrameworkLoader(OSGiAdapter adapter, File[] extensions, File[] libraries, File[] bundles)
            throws BundleException {
        extensionFiles = extensions;
        libraryFiles = libraries;
        bundleFiles = bundles;
        osgi = adapter;
    }

    protected static File[] newSortedFiles(File[] files) {
        File[] sortedFiles = new File[files.length];
        System.arraycopy(files, 0, sortedFiles, 0, files.length);
        Arrays.sort(sortedFiles);
        return sortedFiles;
    }

    public static synchronized void initialize(File home, File[] extensions, File[] libraries, File[] bundles,
            Map<String, String> env) throws IOException, BundleException {
        if (loader != null) {
            throw new IllegalStateException("Framework already initialized.");
        }
        loader = new OSGiFrameworkLoader(new OSGiAdapter(env), newSortedFiles(extensions), newSortedFiles(libraries),
                newSortedFiles(bundles));
        loader.doInitialize();
    }

    public static synchronized void start() throws BundleException, IOException {
        if (loader == null) {
            throw new IllegalStateException("Framework is not initialized. Call initialize method first");
        }
        loader.doStart();
    }

    public static synchronized void stop() throws BundleException {
        loader.doStop();
    }

    protected void doInitialize() throws BundleException, IOException {
        for (File f : extensionFiles) {
            osgi.installBundle(f.toURI());
        }
        osgi.start(); // install hooks and start
        for (File f : libraryFiles) {
            osgi.installBundle(f.toURI());
        }
        for (File f : bundleFiles) {
            osgi.installBundle(f.toURI());
        }
    }

    protected void doStart() throws BundleException, IOException {
        osgi.setSynchMode("flush"); // flush deferred components activation
        osgi.setStartLevel(1); // start remaining bundles
    }

    protected void doStop() throws BundleException {
        osgi.shutdown();
    }

    public static URL[] getURLs() {
        List<URL> urls = new ArrayList<URL>();
        for (Bundle bundle : loader.osgi.getInstalledBundles()) {
            try {
                urls.add(new URL(bundle.getLocation()));
            } catch (MalformedURLException e) {
                ;
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    public static void uninstall(String symbolicName) throws BundleException {
        Bundle bundle = loader.osgi.getBundle(symbolicName);
        if (bundle != null) {
            loader.osgi.uninstall(bundle);
        }
    }

    public static ClassLoader getOSGiLoader() {
        return loader.osgi.getSystemLoader();
    }

    public static String installBundle(File f) throws IOException, BundleException {
        return loader.osgi.installBundle(f.toURI()).getSymbolicName();
    }

    public static String installLibrary(File f) throws IOException, BundleException {
        return installBundle(f);
    }

}
