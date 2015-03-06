/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.runtime.model.impl;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.nuxeo.runtime.model.RuntimeContext;
import org.nuxeo.runtime.model.StreamRef;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public abstract class AbstractRuntimeContext implements RuntimeContext {

    private static final Log log = LogFactory.getLog(RuntimeContext.class);

    protected final State UNREGISTERED = new Unregistered();

    protected final State REGISTERED = new Registered();

    protected final State RESOLVING = new Resolving();

    protected final State RESOLVED = new Resolved();

    protected final State STARTING = new Starting();

    protected final State ACTIVATED = new Activated();

    protected final State STOPPING = new Stopping();

    protected final State UNRESOLVING = new Unresolving();

    protected final String name;

    protected AbstractRuntimeService runtime;

    protected State state = UNREGISTERED;

    protected final ComponentDescriptorReader reader = new ComponentDescriptorReader();

    protected final Map<String, RegistrationInfo[]> deployedFiles = new Hashtable<>();

    protected final Set<RegistrationInfoImpl> registeredInfos = new HashSet<>();

    protected final Set<RegistrationInfoImpl> pendingInfos = new HashSet<>();

    protected final Set<RegistrationInfoImpl> resolvedInfos = new HashSet<>();

    protected final Set<AbstractRuntimeContext> requiredPendingContexts = new HashSet<>();

    protected final Set<AbstractRuntimeContext> requiredContexts = new HashSet<>();

    protected final Set<AbstractRuntimeContext> dependsOnMeContexts = new HashSet<>();

    protected AbstractRuntimeContext(String name) {
        this.name = name;
    }

    public void register(AbstractRuntimeService runtime) {
        state.register(runtime);
    }

    public boolean isRegistered() {
        return state == REGISTERED;
    }

    public boolean isResolved() {
        return state == RESOLVED;
    }

    protected void handleActivated() throws RuntimeServiceException {
        for (AbstractRuntimeContext other : dependsOnMeContexts) {
            other.requiredPendingContexts.remove(this);
            other.requiredContexts.add(this);
        }
    }

    @Override
    public boolean isActivated() {
        return state == ACTIVATED;
    }

    @Override
    public RuntimeService getRuntime() {
        return runtime;
    }

    @Override
    public URL getResource(String name) {
        return Thread.currentThread().getContextClassLoader().getResource(name);
    }

    @Override
    public URL getLocalResource(String name) {
        return Thread.currentThread().getContextClassLoader().getResource(name);
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return Thread.currentThread().getContextClassLoader().loadClass(className);
    }

    @Override
    public RegistrationInfo[] deploy(URL url) throws RuntimeServiceException {
        String key = url.toExternalForm();
        if (deployedFiles.containsKey(key)) {
            throw new RuntimeServiceException("Already deployed " + url);
        }
        RegistrationInfoImpl[] ris = new RegistrationInfoImpl[0];
        if (url.getPath().endsWith("blacklist")) {
            runtime.loadBlacklist(url);
        } else if (url.getPath().endsWith("properties")) {
            runtime.loadProperties(url);
        } else if (url.getPath().endsWith("deployment-fragment.xml")) {
            ; // skip
        } else {
            try {
                ris = reader.read(this, url);
            } catch (IOException e) {
                throw new RuntimeServiceException("Cannot read components of " + url, e);
            }
        }
        for (RegistrationInfoImpl ri : ris) {
            ComponentName name = ri.getName();
            if (name == null) {
                // not parsed correctly, e.g., faces-config.xml
                continue;
            }

            log.debug("Deploying component " + name + " from " + url);

            registeredInfos.add(ri);
            pendingInfos.add(ri);
            runtime.getComponentManager().register(ri);
        }
        deployedFiles.put(key, ris);
        return ris;
    }

    @Override
    public RegistrationInfo[] deploy(StreamRef ref) throws RuntimeServiceException {
        return deploy(ref.asURL());
    }

    @Override
    public void undeploy(URL url) {
        String key = url.toExternalForm();
        RegistrationInfo[] infos = deployedFiles.remove(key);
        if (infos == null) {
            throw new RuntimeServiceException("no registration for " + url);
        }
        TryCompanion.<Void> of(RuntimeServiceException.class)
                .forEachAndCollect(
                        Stream.of(infos),
                        runtime.getComponentManager()::unregister);
    }

    @Override
    public void undeploy(StreamRef ref) {
        undeploy(ref.asURL());
    }

    @Override
    public boolean isDeployed(URL url) {
        String key = url.toExternalForm();
        return deployedFiles.containsKey(key);
    }

    @Override
    public boolean isDeployed(StreamRef ref) {
        return isDeployed(ref.asURL());
    }

    @Override
    public RegistrationInfo[] deploy(String location) throws RuntimeServiceException {
        URL url = getLocalResource(location);
        if (url == null) {
            log.warn("No local resources was found with this name: " + location);
            return new RegistrationInfoImpl[0];
        }
        return deploy(url);
    }

    @Override
    public void undeploy(String location) throws RuntimeServiceException {
        URL url = getLocalResource(location);
        if (url != null) {
            undeploy(url);
        } else {
            log.warn("No local resources was found with this name: " + location);
        }
    }

    @Override
    public boolean isDeployed(String location) {
        URL url = getLocalResource(location);
        if (url != null) {
            return isDeployed(url);
        } else {
            log.warn("No local resources was found with this name: " + location);
            return false;
        }
    }

    @Override
    public void destroy() {
        if (state != UNREGISTERED) {
            unregister();
        }
    }

    @Override
    public RegistrationInfo[] getRegisteredInfos() {
        return registeredInfos.toArray(new RegistrationInfo[registeredInfos.size()]);
    }

    @Override
    public RegistrationInfo[] getPendingInfos() {
        return pendingInfos.toArray(new RegistrationInfo[pendingInfos.size()]);
    }

    @Override
    public RegistrationInfo[] getResolvedInfos() {
        return resolvedInfos.toArray(new RegistrationInfo[resolvedInfos.size()]);
    }

    public AbstractRuntimeContext[] getRequiredContexts() {
        return requiredContexts.toArray(new AbstractRuntimeContext[requiredContexts.size()]);
    }

    public AbstractRuntimeContext[] getRequiredPendingContexts() {
        return requiredPendingContexts.toArray(new AbstractRuntimeContext[requiredPendingContexts.size()]);
    }

    @Override
    public ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    @Override
    public int getState() {
        return state.value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AbstractRuntimeContext other = (AbstractRuntimeContext) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    protected void handleComponentResolved(RegistrationInfoImpl info) throws RuntimeServiceException {
        pendingInfos.remove(info);
        resolvedInfos.add(info);
        boolean requiredActivated = true;
        for (RegistrationInfoImpl other : info.requiredResolved) {
            if (this == other.context) {
                continue;
            }
            other.context.dependsOnMeContexts.add(this);
            if (!other.isActivated()) {
                requiredPendingContexts.add(other.context);
                requiredActivated = false;
            } else {
                requiredContexts.add(other.context);
            }
        }
        if (isRegistered()) {
            if (pendingInfos.isEmpty() && requiredPendingContexts.isEmpty()) {
                resolve();
            }
        } else if (isActivated()) {
            if (requiredActivated) {
                if (!info.isActivated()) {
                    info.activate();
                }
            }
        }
    }

    protected void handleComponentActivated(RegistrationInfoImpl info) {
    }

    public void resolve() {
        state.resolve();
    }

    public void activate() {
        state.resolve().activate();
    }

    public void deactivate() {
        state.deactivate();
    }

    public void unresolve() {
        state.deactivate().unresolve();
    }

    public void unregister() {
        state.deactivate().unresolve().unregister();
    }

    protected abstract class State {

        public final int value;

        State(int value) {
            this.value = value;
        }

        State register(AbstractRuntimeService runtime) {
            return this;
        }

        State resolve() {
            return this;
        }

        State activate() {
            return this;
        }

        State deactivate() {
            return this;
        }

        State unresolve() {
            return this;
        }

        State unregister() {
            handleUnregistering();
            return state = UNREGISTERED;
        }

        @Override
        public String toString() {
            return name + "(" + getClass().getSimpleName() + ")";
        }
    }

    protected class Unregistered extends State {
        Unregistered() {
            super(RuntimeContext.UNREGISTERED);
        }

        @Override
        State register(AbstractRuntimeService runtime) {
            handleRegistering(runtime);
            return state = REGISTERED;
        }

    }

    protected Stream<String> aliases() {
        return Stream.empty();
    }

    protected void handleRegistering(AbstractRuntimeService runtime) {
        AbstractRuntimeContext.this.runtime = runtime;
        runtime.contextsByName.put(name, this);
        aliases().forEach(name -> runtime.contextsByName.put(name, this));
    }

    protected void handleUnregistering() {
        try {
            HashSet<RegistrationInfoImpl> infos = new HashSet<>(registeredInfos);
            TryCompanion.<Void> of(RuntimeServiceException.class)
                    .forEachAndCollect(
                            infos.stream(),
                            runtime.manager::unregister)
                    .orElseThrow(() -> new RuntimeServiceException("Caught errors while unregistering " + this));
        } finally {
            aliases().forEach(name -> runtime.contextsByName.remove(name));
            runtime.contextsByName.remove(name);
            runtime = null;
        }
    }

    protected class Registered extends State {
        Registered() {
            super(RuntimeContext.REGISTERED);
        }

        @Override
        State resolve() {
            if (!pendingInfos.isEmpty()) {
                Set<ComponentName> pendings = new HashSet<>();
                for (RegistrationInfoImpl pending : pendingInfos) {
                    if (pending.optional) {
                        continue;
                    }
                    pendings.addAll(pending.requiredPendings);
                    continue;
                }
                throw new RuntimeServiceException("Cannot resolve " + name + ", waiting for " + pendings);
            }
            state = RESOLVING;
            try {
                handleResolving();
            } catch (RuntimeException cause) {
                state = REGISTERED;
                throw cause;
            }
            handleResolving();
            return state = RESOLVED;
        }

    }

    protected void handleResolving() {
        TryCompanion.<Void> of(RuntimeServiceException.class)
                .forEachAndCollect(
                        requiredPendingContexts.stream(),
                        AbstractRuntimeContext::resolve)
                .orElseThrow(() -> new RuntimeServiceException("Caught errors while resolving " + this));
    }

    protected void handleUnresolving() {
        TryCompanion.<Void> of(RuntimeServiceException.class)
                .forEachAndCollect(new HashSet<RegistrationInfoImpl>(resolvedInfos).stream(),
                        RegistrationInfoImpl::unresolve)
                .orElseThrow(() -> new RuntimeServiceException("Caught errors while unresolving " + this));
    }

    protected class Resolving extends State {
        Resolving() {
            super(RuntimeContext.RESOLVING);
        }

        @Override
        State resolve() {
            return state;
        }

        @Override
        State activate() {
            throw new IllegalStateException(toString());
        }

        @Override
        State unresolve() {
            throw new IllegalStateException(toString());
        }
    }

    protected class Resolved extends State {
        Resolved() {
            super(RuntimeContext.RESOLVED);
        }

        @Override
        State resolve() {
            return state;
        }

        @Override
        State activate() {
            state = STARTING;
            return state.activate();
        }

        @Override
        State unresolve() {
            state = UNRESOLVING;
            try {
                handleUnresolving();
            } finally {
                state = REGISTERED;
            }
            return state;
        }
    }

    protected class Unresolving extends State {
        Unresolving() {
            super(RuntimeContext.UNRESOLVING);
        }

        @Override
        State resolve() {
            throw new IllegalStateException(toString());
        }

        @Override
        State activate() {
            throw new IllegalStateException(toString());
        }

        @Override
        State unresolve() {
            return state;
        }
    }

    protected class Starting extends State {
        Starting() {
            super(RuntimeContext.STARTING);
        }

        @Override
        State resolve() {
            return state;
        }

        @Override
        State activate() {
            handleActivating();
            state = ACTIVATED;
            handleActivated();
            return state;
        }

        @Override
        State deactivate() {
            return state = RESOLVED;
        }
    }

    protected void handleActivating() {
        for (RegistrationInfoImpl info : resolvedInfos) {
            info.lazyActivate();
        }
    }

    protected class Activated extends State {
        Activated() {
            super(RuntimeContext.ACTIVATED);
        }

        @Override
        State resolve() {
            return this;
        }

        @Override
        State activate() {
            return this;
        }

        @Override
        State unresolve() {
            return deactivate().unresolve();
        }

        @Override
        State deactivate() {
            state = STOPPING;
            for (RegistrationInfoImpl info : resolvedInfos) {
                info.deactivate();
            }
            return state.deactivate();
        }
    }

    protected class Stopping extends State {
        Stopping() {
            super(RuntimeContext.STOPPING);
        }

        @Override
        State resolve() {
            return this;
        }

        @Override
        State activate() {
            return this;
        }

        @Override
        State deactivate() {
            return state = RESOLVED;
        }
    }

    @Override
    public String toString() {
        return name + getState();
    }

    public void handleComponentBlacklisted(RegistrationInfoImpl registrationInfo) {
        pendingInfos.remove(registrationInfo);
    }

    public void handleComponentUnregistered(RegistrationInfoImpl info) {
        registeredInfos.remove(info);
        pendingInfos.remove(info);
        info.context = null;
    }

    public void handleComponentUnresolved(RegistrationInfoImpl info) {
        resolvedInfos.remove(info);
        registeredInfos.add(info);
    }

}
