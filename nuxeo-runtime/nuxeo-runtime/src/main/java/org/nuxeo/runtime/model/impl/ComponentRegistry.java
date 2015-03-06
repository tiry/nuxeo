/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.RegistrationInfo;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class ComponentRegistry {

    private final Log log = LogFactory.getLog(ComponentRegistry.class);

    /**
     * All registered components including unresolved ones. You can check the state of a component for getting the
     * unresolved ones.
     */
    protected final Map<ComponentName, RegistrationInfoImpl> components = new HashMap<ComponentName, RegistrationInfoImpl>();

    /** Map of aliased name to canonical name. */
    protected final Map<ComponentName, ComponentName> aliases = new HashMap<ComponentName, ComponentName>();

    protected final MappedSet<ComponentName, RegistrationInfo> requiredPendings = new MappedSet<ComponentName, RegistrationInfo>();

    protected final ComponentManagerImpl manager;

    protected ComponentRegistry(ComponentManagerImpl owner) {
        manager = owner;
    }

    public void destroy() {
        components.clear();
        aliases.clear();
        requiredPendings.clear();
    }

    protected ComponentName unaliased(ComponentName name) {
        ComponentName alias = aliases.get(name);
        return alias == null ? name : alias;
    }

    public final boolean isResolved(ComponentName name) {
        RegistrationInfo ri = components.get(unaliased(name));
        if (ri == null) {
            return false;
        }
        return ri.getState() > RegistrationInfo.REGISTERED;
    }

    /**
     * @param ri
     * @return true if the component was resolved, false if the component is pending
     */
    public boolean addComponent(RegistrationInfoImpl ri) throws RuntimeServiceException {
        // update registry
        ComponentName name = ri.getName();
        Set<ComponentName> al = ri.getAliases();
        components.put(name, ri);
        for (ComponentName n : al) {
            aliases.put(n, name);
        }
        if (!al.isEmpty()) {
            log.info("Aliasing component: " + name + " -> " + al);
        }

        registerPendings(ri);

        return ri.isResolved();
    }

    @SuppressWarnings("unchecked")
    protected void registerPendings(RegistrationInfoImpl ri) {
        for (ComponentName pending : ri.requiredPendings) {
            requiredPendings.put(pending, ri);
        }
        Set<RegistrationInfo> dependsOnMe = new HashSet<>();
        dependsOnMe.addAll(requiredPendings.remove(ri.getName()));
        for (ComponentName alias : ri.getAliases()) {
            dependsOnMe.addAll(requiredPendings.remove(alias));
        }
        ri.register((Set) dependsOnMe);
    }

    public RegistrationInfoImpl removeComponent(ComponentName name) {
        // update registry
        RegistrationInfoImpl ri = components.remove(name);
        if (ri == null) {
            return null;
        }
        for (ComponentName alias : ri.aliases) {
            aliases.remove(alias);
        }
        if (ri.isResolved()) {
            for (ComponentName pending : ri.requiredPendings) {
                requiredPendings.remove(pending, ri);
            }
        }
        TryCompanion.<Void> of(RuntimeException.class)
                .run(() -> ri.unresolve())
                .run(() -> ri.unregister())
                .orElseThrow(() -> new RuntimeServiceException("Removing component " + name));
        return ri;
    }

    public Set<ComponentName> getMissingDependencies(ComponentName name) {
        return components.get(unaliased(name)).requiredPendings;
    }

    public RegistrationInfoImpl getComponent(ComponentName name) {
        return components.get(unaliased(name));
    }

    public boolean contains(ComponentName name) {
        return components.containsKey(unaliased(name));
    }

    public int size() {
        return components.size();
    }

    public Collection<RegistrationInfoImpl> getComponents() {
        return components.values();
    }

    public RegistrationInfoImpl[] getComponentsArray() {
        return components.values().toArray(new RegistrationInfoImpl[components.size()]);
    }

    public void shutdown() {
        TryCompanion.<Void> of(RuntimeServiceException.class)
                .forEachAndCollect(
                        new HashSet<RegistrationInfoImpl>(components.values())
                                .stream()
                                .map(RegistrationInfo::getName),
                        this::removeComponent)
                .orElseThrow(() -> new RuntimeServiceException("caught errors while shutdowning registry"));
    }

}
