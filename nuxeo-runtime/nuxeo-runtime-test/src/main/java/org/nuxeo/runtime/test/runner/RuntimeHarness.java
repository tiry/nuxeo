/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Damien Metzler (Leroy Merlin, http://www.leroymerlin.fr/)
 */
package org.nuxeo.runtime.test.runner;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import org.nuxeo.osgi.bootstrap.application.OSGiAdapter;
import org.nuxeo.runtime.model.RuntimeContext;
import org.nuxeo.runtime.test.WorkingDirectoryConfigurator;
import org.nuxeo.runtime.test.runner.DefaultRuntimeHarness.DeploymentScope;

/**
 * TODO: Move this to org.nuxeo.runtime package
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public interface RuntimeHarness {

    /**
     * Gets the framework working directory.
     */
    File getWorkingDir();

    /**
     * Fires the event {@code FrameworkEvent.STARTED}.
     */
    void fireFrameworkStarted();

    /**
     * Deploys a whole OSGI bundle.
     * <p>
     * The lookup is first done on symbolic name, as set in <code>MANIFEST.MF</code> and then falls back to the bundle
     * url (e.g., <code>nuxeo-platform-search-api</code>) for backwards compatibility.
     *
     * @param bundle the symbolic name
     */
    RuntimeContext deployBundle(String bundle);


    /**
     * Deploys an XML contribution from outside a bundle.
     * <p>
     * This should be used by tests wiling to deploy test contribution as part of a real bundle.
     * <p>
     * The bundle owner is important since the contribution may depend on resources deployed in that bundle.
     * <p>
     * Note that the owner bundle MUST be an already deployed bundle.
     *
     * @param bundle the bundle that becomes the contribution owner
     * @param contrib the contribution to deploy as part of the given bundle
     */
    RuntimeContext deployTestContrib(String bundle, String contrib);

    RuntimeContext deployTestContrib(String bundle, URL contrib);

    /**
     * Deploys a contribution from a given bundle.
     * <p>
     * The path will be relative to the bundle root. Example: <code>
     * deployContrib("org.nuxeo.ecm.core", "OSGI-INF/CoreExtensions.xml")
     * </code>
     * <p>
     * For compatibility reasons the name of the bundle may be a jar name, but this use is discouraged and deprecated.
     *
     * @param bundle the name of the bundle to peek the contrib in
     * @param contrib the path to contrib in the bundle.
     */
    RuntimeContext deployContrib(String bundle, String contrib);

    void start();

    void stop();

    boolean isStarted();

    void deployFolder(File folder, ClassLoader loader);

    void addWorkingDirectoryConfigurator(WorkingDirectoryConfigurator config);

    /**
     * Framework properties for variable injections
     *
     * @since 5.4.2
     */
    Properties getProperties();

    /**
     * Runtime context for deployment
     *
     * @since 5.4.2
     */
    RuntimeContext getContext();

    /**
     * OSGI bridge
     *
     * @since 5.4.2
     */
    OSGiAdapter getOSGiAdapter();

    /**
     * @since 5.5
     */
    public boolean isRestart();

    /**
     * @since 5.5
     * @throws Exception
     */
    public void restart();

    /**
     * @throws URISyntaxException
     * @since 5.7
     */
    public List<String> getClassLoaderFiles();

    /**
     * Push a new scope on the deployment stack
     *
     * @since 7.2
     */
    void pushDeploymentScope();

    /**
     * Pop the current scope from the deployment stack and uninstall all contained deployments
     *
     * @since 7.2
     */
    void popDeploymentScope();

    DeploymentScope deploymentScope();

    /**
     * Locate resource in target classes
     *
     * @return
     * @throws IOException
     */
    URL getResource(String pathname);

}
