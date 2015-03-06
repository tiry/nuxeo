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
 *     Nuxeo - initial API and implementation
 * $Id$
 */

package org.nuxeo.osgi.bootstrap.application;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.nuxeo.common.LoaderConstants;
import org.nuxeo.osgi.bootstrap.OSGiBootstrap;
import org.nuxeo.osgi.bootstrap.OSGiEnvironment;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Requirement;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.repository.Repository;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class OSGiAdapter implements LoaderConstants {

    Bundle system;

    final Map<String, String> config = new HashMap<>();

    public OSGiAdapter(Bundle system) { // backward compat in runtime harness
        this.system = system;
    }

    public OSGiAdapter(Map<String, String> env) {
        config.putAll(env);
    }

    public void start() throws BundleException {
        system = new OSGiBootstrap().newFramework(config);
        system.start();
    }

    public void initialize() throws BundleException {
        start();
    }

    public String getHome() {
        return system.adapt(OSGiEnvironment.class).homeDir.toURI().toASCIIString();
    }

    public void shutdown() throws BundleException {
        system.stop();
    }

    public File getWorkingDir() {
        return system.adapt(OSGiEnvironment.class).workingDir;
    }

    public File getDataDir() {
        return system.adapt(OSGiEnvironment.class).dataDir;
    }

    public Bundle getBundle(String symbolicName) {
        final BundleContext context = system.getBundleContext();
        final BundleRevision revision = system.adapt(BundleRevision.class);
        final ServiceReference<Repository> ref = context.getServiceReference(Repository.class);
        final Repository repository = context.getService(ref);
        try {
            final Requirement requirement = repository
                    .newRequirementBuilder(BundleNamespace.BUNDLE_NAMESPACE)
                    .setResource(revision)
                    .addAttribute(BundleNamespace.BUNDLE_NAMESPACE, symbolicName)
                    .build();
            final List<Requirement> request = Collections.singletonList(requirement);
            return repository
                    .findProviders(request)
                    .get(requirement)
                    .stream()
                    .findFirst()
                    .map(BundleCapability.class::cast)
                    .map(BundleCapability::getRevision)
                    .map(BundleRevision::getBundle)
                    .orElse(null);
        } finally {
            context.ungetService(ref);
        }
    }

    public Bundle[] getInstalledBundles() {
        return system.getBundleContext().getBundles();
    }

    public Bundle installBundle(URI location) throws BundleException {
        return system.getBundleContext().installBundle(location.toASCIIString());
    }

    public Bundle installLibrary(URI location) throws BundleException {
        return system.getBundleContext().installBundle(location.toASCIIString());
    }

    public void uninstall(Bundle bundle) throws BundleException {
        bundle.uninstall();
    }

    public void fireFrameworkEvent(FrameworkEvent event) {
        system.adapt(FrameworkListener.class).frameworkEvent(event);
    }

    public void fireBundleEvent(BundleEvent event) {
        system.adapt(BundleListener.class).bundleChanged(event);
    }

    public Bundle getSystemBundle() {
        return system;
    }

    public ClassLoader getSystemLoader() {
        return system.getClass().getClassLoader();
    }

    public void setProperties(Properties properties) {
        for (final Map.Entry<Object, Object> entry : properties.entrySet()) {
            setProperty(entry.getKey().toString(), entry.getValue().toString());
        }
    }

    public void setProperty(String key, String value) {
        config.put(key, value);
    }

    public String getProperty(String key, String defaultValue) {
        return system.adapt(Properties.class).getProperty(key, defaultValue);
    }

    public void setHome(File file) {
        config.put(LoaderConstants.HOME_DIR, file.toString());
    }

    public void setInitialStartLevel(int level) {
        system.adapt(FrameworkStartLevel.class).setInitialBundleStartLevel(level);
    }

    public void setStartLevel(int level) {
        system.adapt(FrameworkStartLevel.class).setStartLevel(level);
    }

    public void setSynchMode(String value) throws IOException {
        final ServiceReference<ConfigurationAdmin> adminRef = system.getBundleContext()
                .getServiceReference(ConfigurationAdmin.class);
        final ConfigurationAdmin admin = system.getBundleContext().getService(adminRef);
        try {
            final Configuration runtimeConfig = admin.getConfiguration("org.nuxeo.runtime.osgi");
            final Dictionary<String, Object> dict = runtimeConfig.getProperties();
            dict.put("synch", value);
            runtimeConfig.update(dict);
        } finally {
            system.getBundleContext().ungetService(adminRef);
        }
    }
}
