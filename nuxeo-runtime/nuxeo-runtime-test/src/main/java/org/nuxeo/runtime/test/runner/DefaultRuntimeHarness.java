/*
 * (C) Copyright 2006-2013 Nuxeo SA (http://nuxeo.com/) and contributors.
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

package org.nuxeo.runtime.test.runner;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Stack;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.osgi.bootstrap.application.OSGiAdapter;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.nuxeo.runtime.model.RuntimeContext;
import org.nuxeo.runtime.osgi.OSGiRuntimeContext;
import org.nuxeo.runtime.osgi.OSGiRuntimeService;
import org.nuxeo.runtime.test.WorkingDirectoryConfigurator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;
import org.osgi.framework.FrameworkEvent;

public class DefaultRuntimeHarness implements RuntimeHarness {

    protected final Log log = LogFactory.getLog(DefaultRuntimeHarness.class);

    protected final Stack<DeploymentScope> deployments = new Stack<>();

    protected OSGiRuntimeService runtime;

    protected boolean owner = false;

    protected boolean restart = false;

    protected final FeaturesRunner runner;

    public DefaultRuntimeHarness(FeaturesRunner runner) {
        this.runner = runner;
    }

    @Override
    public boolean isRestart() {
        return restart;
    }

    protected final OSGiAdapter adapter = new OSGiAdapter(lookupSystem());

    protected Bundle lookupSystem() {
        return ((BundleReference) DefaultRuntimeHarness.class.getClassLoader()).getBundle()
                .getBundleContext()
                .getBundle(0L);
    }

    protected final List<WorkingDirectoryConfigurator> wdConfigs = new ArrayList<WorkingDirectoryConfigurator>();

    @Override
    public void addWorkingDirectoryConfigurator(WorkingDirectoryConfigurator config) {
        wdConfigs.add(config);
    }

    @Override
    public File getWorkingDir() {
        return adapter.getWorkingDir();
    }

    /**
     * Restarts the runtime and preserve homes directory.
     */
    @Override
    public void restart() {
        restart = true;
        try {
            stop();
            start();
        } finally {
            restart = false;
        }
    }

    @Override
    public void start() {
        { // configure runtime
            AssertionError errors = new AssertionError("Configuring runtime");
            for (WorkingDirectoryConfigurator config : wdConfigs) {
                try {
                    config.configure(this, adapter.getWorkingDir());
                } catch (Exception cause) {
                    errors.addSuppressed(cause);
                }
            }
            if (errors.getSuppressed().length > 0) {
                throw errors;
            }
        }
        runtime = (OSGiRuntimeService) Framework.getRuntime();
        if (!runtime.isStarted()) {
            owner = true;
            runtime.start();
        }
    }

    /**
     * Fire the event {@code FrameworkEvent.STARTED}.
     */
    @Override
    public void fireFrameworkStarted() {
        deployments.peek().activate();
    }

    @Override
    public void stop() {
        try {
            if (owner) {
                Framework.shutdown();
            }
        } finally {
            owner = false;
            runtime = null;
        }
    }

    @Override
    public boolean isStarted() {
        return runtime != null;
    }

    @Override
    public RuntimeContext deployContrib(String name, String contrib) {
        Bundle bundle = adapter.getBundle(name);
        if (bundle == null) {
            throw new IllegalArgumentException("No such bundle " + name);
        }
        URL url = bundle.getResource(contrib);
        if (url == null) {
            throw new AssertionError(String.format("Could not find entry %s in bundle %s", contrib, name));
        }
        RuntimeContext context = runtime.getContext(name);
        if (context == null) {
            throw new AssertionError("Cannot find context of " + name);
        }
        DeploymentScope deployment = deployments.peek();
        deployBundle(name);
        return closerOf(context, deployment.deployContrib(context, url));
    }

    /**
     * Deploy an XML contribution from outside a bundle.
     * <p>
     * This should be used by tests wiling to deploy test contribution as part of a real bundle.
     * <p>
     * The bundle owner is important since the contribution may depend on resources deployed in that bundle.
     * <p>
     * Note that the owner bundle MUST be an already deployed bundle.
     *
     * @param host the bundle that becomes the contribution owner
     * @param contrib the contribution to deploy as part of the given bundle
     */
    @Override
    public RuntimeContext deployTestContrib(String name, String contrib) {
        Bundle bundle = adapter.getBundle(name);
        if (bundle == null) {
            throw new AssertionError("Bundle not deployed " + name + ", cannot deploy " + contrib);
        }
        OSGiRuntimeContext ctx = runtime.getContext(bundle);
        if (ctx == null) {
            deployBundle(name);
            ctx = runtime.getContext(bundle);
        }
        RegistrationInfo[] infos = deployments.peek().deployContrib(ctx, ctx.getLocalResource(contrib));
        return closerOf(ctx, infos);
    }

    @Override
    public RuntimeContext deployTestContrib(String name, URL contrib) {
        Bundle bundle = adapter.getBundle(name);
        if (bundle == null) {
            throw new AssertionError("Bundle not deployed " + bundle);
        }
        OSGiRuntimeContext ctx = runtime.getContext(bundle);
        if (ctx == null) {
            deployBundle(name);
            ctx = runtime.getContext(bundle);
        }
        return closerOf(ctx, deployments.peek().deployContrib(ctx, contrib));
    }

    protected static boolean isVersionSuffix(String s) {
        if (s.length() == 0) {
            return true;
        }
        return s.matches("-(\\d+\\.?)+(-SNAPSHOT)?(\\.\\w+)?");
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
    public RuntimeContext deployBundle(String name) {
        try {
            return deployments.peek().deployBundle(name);
        } catch (BundleException cause) {
            throw new AssertionError("Cannot deploy " + name, cause);
        }
    }

    @Override
    public void deployFolder(File folder, ClassLoader loader) {
        try {
            adapter.installBundle(folder.toURI());
        } catch (BundleException cause) {
            throw new AssertionError("Cannot deploy folder " + folder, cause);
        }
    }

    @Override
    public Properties getProperties() {
        return runtime.getProperties();
    }

    @Override
    public RuntimeContext getContext() {
        return runtime.getContext();
    }

    @Override
    public OSGiAdapter getOSGiAdapter() {
        return adapter;
    }

    @Override
    public List<String> getClassLoaderFiles() {
        List<String> files = new LinkedList<String>();
        for (Bundle bundle : adapter.getSystemBundle().getBundleContext().getBundles()) {
            int state = bundle.getState();
            if ((state & (Bundle.STARTING | Bundle.ACTIVE)) != 0) {
                files.add(bundle.getLocation());
            }
        }
        return files;
    }

    @Override
    public void pushDeploymentScope() {
        if (!deployments.isEmpty()) {
            deployments.peek().activate();
        }
        deployments.push(new DeploymentScope());
    }

    @Override
    public void popDeploymentScope() {
        deployments.pop().undeploy();
    }

    @Override
    public DeploymentScope deploymentScope() {
        return deployments.peek();
    }

    static RuntimeContext closerOf(RuntimeContext context, RegistrationInfo[] infos) {
            return (RuntimeContext)Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class<?>[] { RuntimeContext.class},
                    new InvocationHandler() {

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if ("close".equals(method.getName())) {
                        for (RegistrationInfo info : infos) {
                            info.getContext().undeploy(info.getXmlFileUrl());
                        }
                    }
                    return method.invoke(context, args);
                }
            });
        }

    public class DeploymentScope {
        LinkedList<OSGiRuntimeContext> deployedContexts = new LinkedList<>();

        LinkedList<RegistrationInfo> deployedInfos = new LinkedList<>();

        boolean activated;

        void undeploy() {
            RuntimeServiceException errors = new RuntimeServiceException("See suppressed");
            {
                Iterator<RegistrationInfo> infos = deployedInfos.descendingIterator();
                while (infos.hasNext()) {
                    RegistrationInfo info = infos.next();
                    infos.remove();
                    if (info.getState() == RegistrationInfo.UNREGISTERED) {
                        continue;
                    }
                    try {
                        info.getContext().undeploy(info.getXmlFileUrl());
                    } catch (RuntimeServiceException cause) {
                        errors.addSuppressed(cause);
                    }
                }
            }
            {
                Iterator<OSGiRuntimeContext> contexts = deployedContexts.descendingIterator();
                while (contexts.hasNext()) {
                    OSGiRuntimeContext context = contexts.next();
                    contexts.remove();
                    try {
                        context.unregister();
                    } catch (RuntimeException cause) {
                        errors.addSuppressed(cause);
                    }
                }
            }
            errors.throwOnError();
        }

        void activate() {
            if (!activated) {
                activated = true;
                for (OSGiRuntimeContext context : deployedContexts) {
                    context.activate();
                }
            }
            adapter.fireFrameworkEvent(new FrameworkEvent(FrameworkEvent.STARTED, adapter.getSystemBundle(), null));
        }

        RuntimeContext deployBundle(String name) throws BundleException {
            Bundle bundle = adapter.getBundle(name);
            if (bundle == null) {
                throw new AssertionError(name + " is not installed, check class path");
            }
            if ((bundle.getState()&Bundle.ACTIVE) == 0) {
                bundle.start();
            }
            return runtime.getContext(bundle);
       }

        RegistrationInfo[] deployContrib(RuntimeContext context, URL url) {
            if (url == null) {
                throw new NullPointerException("url is null");
            }
            log.trace("Deploying contribution from " + url.toString());
            RegistrationInfo[] infos = context.deploy(url);
            deployedInfos.addAll(Arrays.asList(infos));
            return infos;
        }

    }

    @Override
    public URL getResource(String pathname) {
        return runner.getTargetTestResource(pathname);
    }

}
