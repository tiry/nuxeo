/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.runtime.test.mockito;

import java.lang.reflect.Field;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.nuxeo.runtime.api.DefaultServiceProvider;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.SimpleFeature;

@Features(RuntimeFeature.class)
public class MockitoFeature extends SimpleFeature {

    protected final MockProvider provider = new MockProvider();

    @Override
    public void start(FeaturesRunner runner) throws Exception {
        provider.installSelf();
    }

    @Override
    public void testCreated(Object test) throws Exception {
        DefaultServiceProvider.setProvider(provider);
        MockitoAnnotations.initMocks(test);
        bindServiceMocks(test);
    }

    protected void bindServiceMocks(Object test) throws IllegalAccessException {
        Field[] fields = test.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Mock.class) && field.isAnnotationPresent(RuntimeService.class)) {
                field.setAccessible(true);
                provider.bind(field.getType(), field.get(test));
            }
        }
    }

    @Override
    public void afterRun(FeaturesRunner runner) throws Exception {
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {
        try {
            provider.uninstallSelf();
        } finally {
            cleanupThread();
        }
    }

    protected void cleanupThread() throws NoSuchFieldException, SecurityException, IllegalArgumentException,
            IllegalAccessException, ClassNotFoundException {
        cleanupThreadLocal("org.mockito.internal.configuration.GlobalConfiguration", "GLOBAL_CONFIGURATION");
        cleanupThreadLocal("org.mockito.internal.progress.ThreadSafeMockingProgress", "mockingProgress");
        return;
    }

    void cleanupThreadLocal(String name, String field) throws NoSuchFieldException, SecurityException,
            IllegalArgumentException, IllegalAccessException, ClassNotFoundException {
        Class<?> type = Mock.class.getClassLoader().loadClass(name);
        Field f = type.getDeclaredField(field);
        f.setAccessible(true);
        ThreadLocal<?> holder = (ThreadLocal<?>) f.get(null);
        holder.remove();
    }

    public <T> T mock(Class<T> type) {
        return Mockito.mock(type);
    }
}
