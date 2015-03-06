/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 */

package org.nuxeo.runtime.test;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import javax.inject.Inject;

import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.nuxeo.osgi.bootstrap.application.OSGiAdapter;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.model.RuntimeContext;
import org.nuxeo.runtime.test.runner.DefaultRuntimeHarness.DeploymentScope;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.RuntimeHarness;

import junit.framework.TestCase;

/**
 * Abstract base class for test cases that require a test runtime service.
 * <p>
 * The runtime service itself is conveniently available as the <code>runtime</code> instance variable in derived
 * classes.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@RunWith(FeaturesRunner.class)
@Features({ RuntimeFeature.class })
public abstract class NXRuntimeTestCase extends TestCase implements RuntimeHarness {

    protected @Inject Mockery jmcontext;

    protected @Inject RuntimeHarness harness;

    protected @Inject RuntimeService runtime;

    @Override
    @Before
    public void setUp() throws Exception {
        fireFrameworkStarted();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        ;
    }

    @Override
    public boolean isRestart() {
        return harness.isRestart();
    }

    @Override
    public void addWorkingDirectoryConfigurator(WorkingDirectoryConfigurator config) {
        harness.addWorkingDirectoryConfigurator(config);
    }

    @Override
    public File getWorkingDir() {
        return harness.getWorkingDir();
    }

    /**
     * Restarts the runtime and preserve homes directory.
     */
    @Override
    public void restart() {
        harness.restart();
    }

    @Override
    public void start()  {
        harness.start();
    }

    /**
     * Fire the event {@code FrameworkEvent.STARTED}.
     */
    @Override
    public void fireFrameworkStarted()  {
        harness.fireFrameworkStarted();
    }

    @Override
    public void stop()  {
        harness.stop();
    }

    @Override
    public boolean isStarted() {
        return harness.isStarted();
    }

    @Override
    public RuntimeContext deployContrib(String name, String contrib) {
        return harness.deployContrib(name, contrib);
    }

    @Override
    public RuntimeContext deployTestContrib(String bundle, String contrib) {
        return harness.deployTestContrib(bundle, contrib);
    }

    @Override
    public RuntimeContext deployTestContrib(String bundle, URL contrib)  {
        return harness.deployTestContrib(bundle, contrib);
    }

    /**
     * Deploys a whole OSGI bundle.
     * <p>
     * The lookup is first done on symbolic name, as set in <code>MANIFEST.MF</code> and then falls back to the bundle
     * url (e.g., <code>nuxeo-platform-search-api</code>) for backwards compatibility.
     *
     * @param host the symbolic name
     */
    @Override
    public RuntimeContext deployBundle(String name)  {
        return harness.deployBundle(name);
    }

    @Override
    public void deployFolder(File folder, ClassLoader loader) {
        harness.deployFolder(folder, loader);
    }

    @Override
    public Properties getProperties() {
        return harness.getProperties();
    }

    @Override
    public RuntimeContext getContext() {
        return harness.getContext();
    }

    @Override
    public OSGiAdapter getOSGiAdapter() {
        return harness.getOSGiAdapter();
    }

    @Override
    public List<String> getClassLoaderFiles() {
        return harness.getClassLoaderFiles();
    }

    @Override
    public void pushDeploymentScope() {
        harness.pushDeploymentScope();
    }

    @Override
    public void popDeploymentScope() {
        harness.popDeploymentScope();
    }

    @Override
    public DeploymentScope deploymentScope() {
        return harness.deploymentScope();
    }

    @Override
    public URL getResource(String pathname) {
        return harness.getResource(pathname);
    }

}
