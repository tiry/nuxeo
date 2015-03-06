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
 *     bstefanescu
 */
package org.nuxeo.runtime.test.runner;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Rule;
import org.junit.rules.MethodRule;
import org.nuxeo.common.Environment;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.nuxeo.runtime.model.RuntimeContext;

import com.google.inject.Binder;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@Deploy({ "org.nuxeo.runtime","org.nuxeo.runtime.management", "org.nuxeo.runtime.metrics", "org.nuxeo.runtime.test" })
@LocalDeploy("org.nuxeo.runtime.management:isolated-server.xml")
@Features({ LoggingFeature.class, JustintimeLoggingFeature.class, MDCFeature.class, ConditionalIgnoreRule.Feature.class,
        RandomBug.Feature.class })
public class RuntimeFeature extends SimpleFeature {

    protected RuntimeHarness harness;

    protected RuntimeDeployment deployment;

    /**
     * Providers contributed by other features to override the default service provider used for a nuxeo service.
     */
    protected final Map<Class<?>, ServiceProvider<?>> serviceProviders;

    public RuntimeFeature() {
        serviceProviders = new HashMap<Class<?>, ServiceProvider<?>>();
    }

    public <T> void addServiceProvider(ServiceProvider<T> provider) {
        serviceProviders.put(provider.getServiceClass(), provider);
    }

    public RuntimeHarness getHarness() {
        return harness;
    }

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        harness = new DefaultRuntimeHarness(runner);
        deployment = RuntimeDeployment.onTest(runner);
        setupRuntimeEnvironment();
        harness.start();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void configure(FeaturesRunner runner, Binder binder) {
        binder.bind(RuntimeHarness.class).toInstance(getHarness());
        binder.bind(RuntimeService.class).toInstance(Framework.getRuntime());
        for (RegistrationInfo info : Framework.getRuntime().getComponentManager().getRegistrations()) {
            if (!info.isActivated()) {
                continue;
            }
            RuntimeContext context = info.getContext();
            for (String name : info.getProvidedServiceNames()) {
                try {
                    Class clazz = context.loadClass(name);
                    ServiceProvider<?> provider = serviceProviders.get(clazz);
                    if (provider == null) {
                        serviceProviders.put(clazz, new ServiceProvider(clazz));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to bind service: " + name, e);
                }
            }
        }
        for (ServiceProvider<?> provider : serviceProviders.values()) {
            Class<?> type = provider.getServiceClass();
            binder.bind((Class) type).toProvider(provider).in(provider.getScope());
        }
    }

    private void setupRuntimeEnvironment() {
        Environment.getDefault().setConfigurationProvider(new Iterable<URL>() {

            @Override
            public Iterator<URL> iterator() {
                File config = Environment.getDefault().getConfig();
                File[] properties = config.listFiles(new FilenameFilter() {

                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".properties");
                    }
                });
                if (properties == null) {
                    return new Iterator<URL>() {

                        @Override
                        public boolean hasNext() {
                            return false;
                        }

                        @Override
                        public URL next() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
                Iterator<File> files = Arrays.asList(properties).iterator();
                return new Iterator<URL>() {

                    @Override
                    public boolean hasNext() {
                        return files.hasNext();
                    }

                    @Override
                    public URL next() {
                        File file = files.next();
                        try {
                            return file.toURI().toURL();
                        } catch (MalformedURLException cause) {
                            throw new AssertionError("Cannot get url of " + file, cause);
                        }
                    }

                };
            }

        });
    }

    @Override
    public void start(final FeaturesRunner runner) throws Exception {
        deployment.deploy(runner, harness);
        harness.pushDeploymentScope();
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {
        AssertionError errors = new AssertionError("Stopping");
        try {
            try {
                harness.popDeploymentScope();
            } catch (RuntimeException cause) {
                errors.addSuppressed(cause);
            } finally {
                try {
                    deployment.undeploy(runner, harness);
                } catch (RuntimeException cause) {
                    errors.addSuppressed(cause);
                }
            }
        } finally {
            try {
                harness.stop();
            } catch (RuntimeException cause) {
                errors.addSuppressed(cause);
                ;
            }
        }
        if (errors.getSuppressed().length > 0) {
            throw errors;
        }
    }

    @Rule
    public MethodRule onMethodDeployment() {
        return RuntimeDeployment.onMethod();
    }

    @Override
    public void beforeRun(FeaturesRunner runner) throws Exception {
        harness.pushDeploymentScope();
    }

    @Override
    public void afterRun(FeaturesRunner runner) throws Exception {
        harness.popDeploymentScope();
    }

    public void deploy(String[] deployments) {
        deployment.index(new Deploy() {

            @Override
            public Class<? extends Annotation> annotationType() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String[] value() {
                return deployments;
            }
        });
    }
}
