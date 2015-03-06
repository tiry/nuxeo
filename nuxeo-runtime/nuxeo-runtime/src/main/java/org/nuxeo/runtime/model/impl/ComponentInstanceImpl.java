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

package org.nuxeo.runtime.model.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.model.Adaptable;
import org.nuxeo.runtime.model.Component;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.Extension;
import org.nuxeo.runtime.model.ExtensionPoint;
import org.nuxeo.runtime.model.Property;
import org.nuxeo.runtime.model.ReloadableComponent;
import org.nuxeo.runtime.model.RuntimeContext;
import org.nuxeo.runtime.service.TimestampedService;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class ComponentInstanceImpl implements ComponentInstance {

    protected Object instance;

    protected RegistrationInfoImpl ri;

    protected final List<OSGiServiceFactory> factories = new LinkedList<ComponentInstanceImpl.OSGiServiceFactory>();

    public ComponentInstanceImpl(RegistrationInfoImpl ri) throws RuntimeServiceException {
        this.ri = ri;
    }

    @Override
    public Object getInstance() {
        return instance;
    }

    void create() {

    }

    @Override
    public void destroy() {
        deactivate();
        instance = null;
        ri = null;
    }

    @Override
    public RuntimeContext getContext() {
        return ri.context;
    }

    @Override
    public ComponentName getName() {
        return ri.name;
    }

    // TODO: cache info about implementation to avoid computing it each time
    @Override
    public void activate() throws RuntimeServiceException {
        if (ri.implementation == null) {
            instance = this; // should be an extension component
        } else {
            try {
                instance = ri.context.loadClass(ri.implementation).newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException cause) {
                throw new RuntimeServiceException("Cannot instantiate " + ri.implementation, cause);
            }
        }
        // activate the implementation instance
        try {
            if (instance instanceof Component) {
                ((Component) instance).activate(this);
            } else { // try by reflection
                Method meth = instance.getClass().getDeclaredMethod("activate", ComponentContext.class);
                meth.setAccessible(true);
                meth.invoke(instance, this);
            }
        } catch (NoSuchMethodException e) {
            // ignore this exception since the activate method is not mandatory
        } catch (Exception e) {
            throw new RuntimeServiceException(this + ": cannot activate", e);
        }
    }

    // TODO: cache info about implementation to avoid computing it each time
    @Override
    public void deactivate() throws RuntimeServiceException {
        try {
            if (instance instanceof Component) {
                ((Component) instance).deactivate(this);
            } else {
                // try by reflection
                Method meth = instance.getClass().getDeclaredMethod("deactivate", ComponentContext.class);
                meth.setAccessible(true);
                meth.invoke(instance, this);
            }
        } catch (NoSuchMethodException e) {
            // ignore this exception since the activate method is not mandatory
        } catch (Exception e) {
            throw new RuntimeServiceException(this + ": cannot deactivate", e);
        }
    }

    @Override
    public void reload() {
        // activate the implementation instance
        try {
            if (instance instanceof ReloadableComponent) {
                ((ReloadableComponent) instance).reload(this);
            } else {
                Method meth = instance.getClass().getDeclaredMethod("reload", ComponentContext.class);
                meth.setAccessible(true);
                meth.invoke(instance, this);
            }
        } catch (NoSuchMethodException e) {
            // ignore this exception since the reload method is not mandatory
        } catch (Exception e) {
            throw new RuntimeServiceException("Failed to reload component: " + getName(), e);
        }
    }

    // TODO: cache info about implementation to avoid computing it each time
    @Override
    public void registerExtension(Extension extension) {
        // if this the target extension point is extending another extension
        // point from another component
        // then delegate the registration to the that component component
        ExtensionPoint xp = ri.getExtensionPoint(extension.getExtensionPoint());
        if (xp != null) {
            String superCo = xp.getSuperComponent();
            if (superCo != null) {
                ((ExtensionImpl) extension).target = new ComponentName(superCo);
                ri.manager.registerExtension(extension);
                return;
            }
        } else {
            throw new RuntimeServiceException("Warning: target extension point '" + extension.getExtensionPoint()
                    + "' of '" + extension.getTargetComponent().getName()
                    + "' is unknown. Check your extension in component " + extension.getComponent().getName());
        }
        // this extension is for us - register it
        // activate the implementation instance
        if (instance instanceof Component) {
            ((Component) instance).registerExtension(extension);
        } else {
            // try by reflection
            try {
                Method meth = instance.getClass().getDeclaredMethod("registerExtension", Extension.class);
                meth.setAccessible(true);
                meth.invoke(instance, extension);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException cause) {
                throw new RuntimeServiceException("cannot register extension " + extension.getId(), cause);
            }
        }
    }

    // TODO: cache info about implementation to avoid computing it each time
    @Override
    public void unregisterExtension(Extension extension) {
        // activate the implementation instance
        if (instance instanceof Component) {
            ((Component) instance).unregisterExtension(extension);
        } else {
            // try by reflection
            try {
                Method meth = instance.getClass().getDeclaredMethod("unregisterExtension", Extension.class);
                meth.setAccessible(true);
                meth.invoke(instance, extension);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException cause) {
                throw new RuntimeServiceException("cannot unregister extension " + extension.getId(), cause);
            }
        }
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        T res = null;
        Object object = getInstance();
        if (object instanceof Adaptable) {
            res = ((Adaptable) object).getAdapter(adapter);
        } else if (adapter.isAssignableFrom(object.getClass())) {
            res = adapter.cast(object);
        }
        // to handle hot reload
        if (res instanceof TimestampedService && object instanceof TimestampedService) {
            Long lastModified = ((TimestampedService) object).getLastModified();
            ((TimestampedService) res).setLastModified(lastModified);
        }
        return res;
    }

    @Override
    public String[] getPropertyNames() {
        Set<String> set = ri.getProperties().keySet();
        return set.toArray(new String[set.size()]);
    }

    @Override
    public Property getProperty(String property) {
        return ri.getProperties().get(property);
    }

    @Override
    public RuntimeContext getRuntimeContext() {
        return ri.getContext();
    }

    @Override
    public Object getPropertyValue(String property) {
        return getPropertyValue(property, null);
    }

    @Override
    public Object getPropertyValue(String property, Object defValue) {
        Property prop = getProperty(property);
        if (prop != null) {
            return prop.getValue();
        } else {
            return defValue;
        }
    }

    @Override
    public String[] getProvidedServiceNames() {
        return ri.getProvidedServiceNames();
    }

    @Override
    public String toString() {
        if (ri == null) {
            return super.toString();
        }
        return ri.toString();
    }

    protected class OSGiServiceFactory implements ServiceFactory<Object> {
        protected Class<?> clazz;

        protected ServiceRegistration<Object> reg;

        public OSGiServiceFactory(String className) throws Exception {
            this(ri.getContext().getBundle(), className);
        }

        public OSGiServiceFactory(Bundle bundle, String className) throws Exception {
            clazz = ri.getContext().getBundle().loadClass(className);
        }

        @Override
        public Object getService(Bundle bundle, ServiceRegistration<Object> registration) {
            return getAdapter(clazz);
        }

        @Override
        public void ungetService(Bundle bundle, ServiceRegistration<Object> registration, Object service) {
            // do nothing
        }

        @SuppressWarnings("unchecked")
        public void register() {
            reg = (ServiceRegistration<Object>) ri.getContext()
                    .getBundle()
                    .getBundleContext()
                    .registerService(clazz.getName(), this, null);
        }

        public void unregister() {
            if (reg != null) {
                reg.unregister();
            }
            reg = null;
        }
    }

}
