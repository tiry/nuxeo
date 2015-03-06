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
 *     Florent Guillaume
 */

package org.nuxeo.runtime.model.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.collections.ListenerList;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.runtime.ComponentEvent;
import org.nuxeo.runtime.ComponentListener;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.Extension;
import org.nuxeo.runtime.model.RegistrationInfo;

/**
 * @author Bogdan Stefanescu
 * @author Florent Guillaume
 */
public class ComponentManagerImpl implements ComponentManager {

    private final Log log = LogFactory.getLog(ComponentManagerImpl.class);

    // must use an ordered Set to avoid loosing the order of the pending
    // extensions
    protected final Map<ComponentName, Set<Extension>> extensionPendingsByComponent;

    private ListenerList<ComponentListener> listeners;

    private final Map<String, RegistrationInfoImpl> services;

    protected Set<String> blacklist = new HashSet<>();

    protected ComponentRegistry reg;

    public ComponentManagerImpl(RuntimeService runtime) {
        reg = new ComponentRegistry(this);
        extensionPendingsByComponent = new HashMap<ComponentName, Set<Extension>>();
        listeners = new ListenerList<>(ComponentListener.class);
        services = new ConcurrentHashMap<String, RegistrationInfoImpl>();
        blacklist = new HashSet<String>();
    }

    @Override
    public Collection<RegistrationInfo> getRegistrations() {
        return new ArrayList<RegistrationInfo>(reg.getComponents());
    }

    @Override
    public Map<ComponentName, Set<RegistrationInfo>> getPendingRegistrations() {
        return reg.requiredPendings.map;
    }

    @Override
    public RegistrationInfoImpl getRegistrationInfo(ComponentName name) {
        return reg.getComponent(name);
    }

    @Override
    public boolean isRegistered(ComponentName name) {
        return reg.contains(name);
    }

    @Override
    public int size() {
        return reg.size();
    }

    @Override
    public ComponentInstanceImpl getComponent(ComponentName name) {
        RegistrationInfoImpl ri = reg.getComponent(name);
        return ri != null ? ri.getComponent() : null;
    }

    @Override
    public void shutdown() {
        TryCompanion.<Void> of(RuntimeException.class)
                .run(() -> reg.shutdown())
                .run(() -> listeners = null)
                .run(() -> reg.destroy())
                .run(() -> reg = null)
                .orElseThrow(() -> new RuntimeServiceException("shutdown component manager"));
    }

    @Override
    public Set<String> getBlacklist() {
        return blacklist;
    }

    @Override
    public void setBlacklist(Set<String> blacklist) {
        this.blacklist = blacklist;
    }

    @Override
    public void register(RegistrationInfo regInfo) throws RuntimeServiceException {
        RegistrationInfoImpl ri = (RegistrationInfoImpl) regInfo;
        ComponentName name = ri.getName();

        if (blacklist.contains(name.getName())) {
            log.warn("Component " + name.getName() + " was blacklisted. Ignoring.");
            ri.context.handleComponentBlacklisted(ri);
            sendEvent(new ComponentEvent(ComponentEvent.COMPONENT_BLACKLISTED, regInfo));
            return;
        }
        if (reg.contains(name)) {
            throw new RuntimeServiceException(this + " : Duplicate component name " + name);
        }
        Set<Extension> pendings = extensionPendingsByComponent.get(ri.name);
        if (pendings == null) {
            pendings = new LinkedHashSet<Extension>();
            extensionPendingsByComponent.put(ri.name, pendings);
        }
        for (ComponentName alias : ri.getAliases()) {
            if (reg.contains(alias)) {
                throw new RuntimeServiceException(
                        this + " : Duplicate component name " + alias + " (alias for " + name + ")");
            }
            Set<Extension> aliasPendings = extensionPendingsByComponent.remove(alias);
            if (aliasPendings != null) {
                pendings.addAll(aliasPendings);
            }
        }
        ri.attach(this);
        if (!reg.addComponent(ri)) {
            log.trace("Registration delayed for component: " + name + ". Waiting for: "
                    + reg.getMissingDependencies(ri.getName()));
        } else {
            log.info("Registered component: " + name);
        }
    }

    @Override
    public void unregister(RegistrationInfo regInfo) {
        unregister(regInfo.getName());
    }

    @Override
    public void unregister(ComponentName name) {
        reg.removeComponent(name);
        log.trace("Unregistered component: " + name);
    }

    @Override
    public void addComponentListener(ComponentListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeComponentListener(ComponentListener listener) {
        listeners.remove(listener);
    }

    @Override
    public ComponentInstance getComponentProvidingService(Class<?> serviceClass) {
        RegistrationInfoImpl ri = services.get(serviceClass.getName());
        if (ri == null) {
            return null;
        }
        return ri.getComponent();
    }

    @Override
    public <T> T getService(Class<T> serviceClass) {
        ComponentInstance comp = getComponentProvidingService(serviceClass);
        return comp != null ? comp.getAdapter(serviceClass) : null;
    }

    @Override
    public Collection<ComponentName> getActivatingRegistrations() {
        RegistrationInfo[] comps = null;
        comps = reg.getComponentsArray();
        Collection<ComponentName> activating = new ArrayList<ComponentName>();
        for (RegistrationInfo ri : comps) {
            if (ri.getState() == RegistrationInfo.ACTIVATING) {
                activating.add(ri.getName());
            }
        }
        return activating;
    }

    void sendEvent(ComponentEvent event) throws RuntimeServiceException {
        if (log.isDebugEnabled()) {
            log.debug("Dispatching event: " + event);
        }
        TryCompanion.<Void> of(RuntimeException.class)
                .forEachAndCollect(
                        Stream.of(listeners.getListeners()),
                        handler -> handler.handleEvent(event))
                .orElseThrow(() -> new RuntimeServiceException("firing " + event));
    }

    public void registerExtension(Extension extension) throws RuntimeServiceException {
        ComponentName name = extension.getTargetComponent();
        RegistrationInfoImpl ri = reg.getComponent(name);
        if (ri != null && ri.component != null) {
            if (log.isDebugEnabled()) {
                log.debug("Register contributed extension: " + extension);
            }
            loadContributions(ri, extension);
            try {
                ri.component.registerExtension(extension);
            } catch (Exception e) {
                throw new RuntimeServiceException(ri + " : cannot register " + extension, e);
            }
            sendEvent(new ComponentEvent(ComponentEvent.EXTENSION_REGISTERED,
                    ((ComponentInstanceImpl) extension.getComponent()).ri, extension));
        } else { // put the extension in the pending queue
            if (log.isDebugEnabled()) {
                log.debug("Enqueue contributed extension to pending queue: " + extension);
            }
            Set<Extension> extensions = extensionPendingsByComponent.get(name);
            if (extensions == null) {
                extensions = new LinkedHashSet<Extension>(); // must keep order
                                                             // in which
                                                             // extensions are
                                                             // contributed
                extensionPendingsByComponent.put(name, extensions);
            }
            extensions.add(extension);
            sendEvent(new ComponentEvent(ComponentEvent.EXTENSION_PENDING,
                    ((ComponentInstanceImpl) extension.getComponent()).ri, extension));
        }
    }

    void loadContributions(RegistrationInfoImpl ri, Extension xt) {
        ExtensionPointImpl xp = ri.getExtensionPoint(xt.getExtensionPoint());
        if (xp == null) {
            throw new IllegalStateException("Cannot load contributions, extension point not registered (" + xt + ")");
        }
        try {
            xp.loadContributions(ri, xt);
        } catch (Exception e) {
            throw new RuntimeServiceException("Failed to create contribution objects", e);
        }
    }

    public void unregisterExtension(Extension extension) throws RuntimeServiceException {
        // TODO check if framework is shutting down and in that case do nothing
        if (log.isDebugEnabled()) {
            log.debug("Unregister contributed extension: " + extension);
        }
        ComponentName name = extension.getTargetComponent();
        RegistrationInfo ri = reg.getComponent(name);
        if (ri != null) {
            ComponentInstance co = ri.getComponent();
            if (co != null) {
                try {
                    co.unregisterExtension(extension);
                } catch (Exception e) {
                    throw new RuntimeServiceException(ri + " : cannot unregister " + extension, e);
                } finally {
                    extension.setContributions(null);
                }
            }
        } else { // maybe it's pending
            Set<Extension> extensions = extensionPendingsByComponent.get(name);
            if (extensions != null) {
                // FIXME: extensions is a set of Extensions, not ComponentNames.
                extensions.remove(name);
                if (extensions.isEmpty()) {
                    extensionPendingsByComponent.remove(name);
                }
            }
        }
        sendEvent(new ComponentEvent(ComponentEvent.EXTENSION_UNREGISTERED,
                ((ComponentInstanceImpl) extension.getComponent()).ri, extension));
    }

    void registerServices(RegistrationInfoImpl ri) {
        if (ri.serviceDescriptor == null) {
            return;
        }
        for (String service : ri.serviceDescriptor.services) {
            log.info("Registering service: " + service);
            services.put(service, ri);
            // TODO: send notifications
        }
    }

    void unregisterServices(RegistrationInfoImpl ri) {
        if (ri.serviceDescriptor == null) {
            return;
        }
        for (String service : ri.serviceDescriptor.services) {
            services.remove(service);
            // TODO: send notifications
        }
    }

    @Override
    public String[] getServices() {
        return services.keySet().toArray(new String[services.size()]);
    }

}
