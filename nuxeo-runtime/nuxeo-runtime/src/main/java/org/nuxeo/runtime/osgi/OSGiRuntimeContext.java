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
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.runtime.osgi;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.collections.Streams;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.nuxeo.runtime.model.impl.AbstractRuntimeContext;
import org.nuxeo.runtime.model.impl.AbstractRuntimeService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class OSGiRuntimeContext extends AbstractRuntimeContext {

    protected final Bundle host;

    protected final Collection<Bundle> fragments;

    protected final Log log = LogFactory.getLog(OSGiRuntimeContext.class);

    public OSGiRuntimeContext(Bundle bundle) {
        super(bundle.getSymbolicName());
        host = bundle;
        fragments = bundle
                .adapt(BundleWiring.class)
                .getProvidedWires(HostNamespace.HOST_NAMESPACE)
                .stream()
                .map(BundleWire::getRequirer)
                .map(BundleRevision::getBundle)
                .flatMap(this::fragmentsOf)
                .collect(Collectors.toList());
    }

    Stream<Bundle> fragmentsOf(Bundle bundle) {
        return Stream.concat(
                Stream.of(bundle),
                bundle
                        .adapt(BundleWiring.class)
                        .getProvidedWires(HostNamespace.HOST_NAMESPACE)
                        .stream()
                        .map(BundleWire::getRequirer)
                        .map(BundleRevision::getBundle)
                        .flatMap(this::fragmentsOf));
    }

    @Override
    public OSGiRuntimeService getRuntime() {
        return (OSGiRuntimeService) runtime;
    }

    @Override
    protected Stream<String> aliases() {
        return fragments.stream()
                .map(Bundle::getSymbolicName);
    }

    @Override
    protected void handleRegistering(AbstractRuntimeService runtime) throws RuntimeServiceException {
        class ComponentLoader {
            void loadComponents() {
                TryCompanion.<Void> of(RuntimeServiceException.class)
                        .run(() -> loadComponents(host))
                        .forEachAndCollect(
                                fragments.stream(),
                                this::loadComponents)
                        .orElseThrow(() -> new RuntimeServiceException("Cannot load some components of " + host));
            }

            void loadComponents(Bundle bundle) {
                String directive = bundle.getHeaders()
                        .get("Nuxeo-Component");
                if (directive == null) {
                    return;
                }
                LogFactory.getLog(ComponentLoader.class)
                        .debug("Loading " + directive + " in " + bundle);
                TryCompanion.<Void> of(RuntimeServiceException.class)
                        .forEachAndCollect(
                                Streams.of(new StringTokenizer(directive, ", \t\n\r\f"))
                                        .map(String.class::cast)
                                        .flatMap(pattern -> locateComponents(bundle, pattern)),
                                this::loadComponents)
                        .orElseThrow(() -> new RuntimeServiceException("Cannot load some components of " + host));
            }

            void loadComponents(URL location) {
                LogFactory.getLog(ComponentLoader.class)
                        .info("loading " + location);
                deploy(location);
            }

            Stream<URL> locateComponents(Bundle bundle, String filepattern) {
                if (filepattern.indexOf('*') == -1) {
                    URL entry = bundle.getEntry(filepattern);
                    if (entry == null) {
                        return Stream.empty();
                    }
                    return Collections.singleton(entry)
                            .stream();
                }
                String path = "/";
                int sepindex = filepattern.lastIndexOf('/');
                if (sepindex > 0) {
                    path = filepattern.substring(0, sepindex);
                    filepattern = filepattern.substring(sepindex + 1);
                }
                return Streams.of(bundle.findEntries(path, filepattern, false));
            }

        }
        super.handleRegistering(runtime);
        new ComponentLoader().loadComponents();
    }

    @Override
    public void activate() {
        ClassLoader last = Thread.currentThread()
                .getContextClassLoader();
        Thread.currentThread()
                .setContextClassLoader(host.adapt(ClassLoader.class));
        try {
            super.activate();
        } finally {
            Thread.currentThread()
                    .setContextClassLoader(last);
        }
    }

    @Override
    public void deactivate() {
        ClassLoader last = Thread.currentThread()
                .getContextClassLoader();
        Thread.currentThread()
                .setContextClassLoader(host.adapt(ClassLoader.class));
        try {
            super.deactivate();
        } finally {
            Thread.currentThread()
                    .setContextClassLoader(last);
        }
    }

    @Override
    protected void handleActivating() {
        if ((host.getState() & Bundle.STARTING) != 0) {
            try {
                host.start();
            } catch (BundleException cause) {
                throw new RuntimeServiceException("Cannot lazy start " + host, cause);
            }
        }
        reader.flushDeferred();
        super.handleActivating();
    }

    @Override
    public Bundle getBundle() {
        return host;
    }

    @Override
    public URL getResource(String name) {
        URL url = host.getResource(name);
        if (url == null) {
            url = Framework.getResourceLoader()
                    .getResource(name);
        }
        return url;
    }

    @Override
    public URL getLocalResource(String name) {
        URL url = host.getResource(name);
        if (url == null) {
            url = Framework.getResourceLoader()
                    .getResource(name);
        }
        return url;
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        try {
            return host.loadClass(className);
        } catch (ClassNotFoundException e) {
            return Framework.getResourceLoader()
                    .loadClass(className);
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        return host.adapt(ClassLoader.class);
    }

    @Override
    public RegistrationInfo[] deploy(URL resource) throws RuntimeServiceException {
        ClassLoader cl = Thread.currentThread()
                .getContextClassLoader();
        Thread.currentThread()
                .setContextClassLoader(host.adapt(ClassLoader.class));
        try {
            return super.deploy(resource);
        } finally {
            Thread.currentThread()
                    .setContextClassLoader(cl);
        }
    }

    @Override
    public String toString() {
        return "OSGiRuntimeContext [bundle=" + host + ", state=" + state.value + "]";
    }

}
