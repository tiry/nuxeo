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
 */

package org.nuxeo.runtime.model.impl;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.trycompanion.Try;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.common.xmap.annotation.XContent;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.ComponentEvent;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.Version;
import org.nuxeo.runtime.model.Component;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.ConfigurationDescriptor;
import org.nuxeo.runtime.model.Extension;
import org.nuxeo.runtime.model.ExtensionPoint;
import org.nuxeo.runtime.model.Property;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.nuxeo.runtime.model.RuntimeContext;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@XObject(value = "component", order = "require,extension")
public class RegistrationInfoImpl implements RegistrationInfo {

    private static final long serialVersionUID = -4135715215018199522L;

    private final Log log = LogFactory.getLog(RegistrationInfoImpl.class);

    // Note: some of these instance variables are accessed directly from other
    // classes in this package.

    protected transient ComponentManagerImpl manager;

    @XNode("@service")
    protected ServiceDescriptor serviceDescriptor;

    // the managed object name
    @XNode("@name")
    protected ComponentName name;

    @XNode("@optional")
    protected boolean optional = false;

    // my aliases
    @XNodeList(value = "alias", type = HashSet.class, componentType = ComponentName.class)
    protected Set<ComponentName> aliases = new HashSet<ComponentName>();

    protected final Set<ComponentName> names = new HashSet<ComponentName>();

    @XNode("@disabled")
    protected boolean disabled;

    @XNode("configuration")
    protected ConfigurationDescriptor config;

    // the registration state
    protected int state = UNREGISTERED;

    // the object names I depend of
    @XNodeList(value = "require", type = HashSet.class, componentType = ComponentName.class)
    protected Set<ComponentName> requires = new HashSet<ComponentName>();

    protected final Set<RegistrationInfoImpl> dependsOnMe = new HashSet<RegistrationInfoImpl>();

    protected final Set<ComponentName> requiredPendings = new HashSet<ComponentName>();

    protected final Set<RegistrationInfoImpl> requiredRegistered = new HashSet<RegistrationInfoImpl>();

    protected final Set<RegistrationInfoImpl> requiredResolved = new HashSet<RegistrationInfoImpl>();

    @XNode("implementation@class")
    protected String implementation;

    @XNodeList(value = "extension-point", type = ExtensionPointImpl[].class, componentType = ExtensionPointImpl.class)
    protected ExtensionPointImpl[] extensionPoints;

    @XNodeList(value = "extension", type = ExtensionImpl[].class, componentType = ExtensionImpl.class)
    protected ExtensionImpl[] extensions;

    @XNodeMap(value = "property", key = "@name", type = HashMap.class, componentType = Property.class)
    protected Map<String, Property> properties;

    @XNode("@version")
    protected Version version = Version.ZERO;

    /**
     * To be set when deploying configuration components that are not in a bundle (e.g. from config. dir). Represent the
     * bundle that will be assumed to be the owner of the component.
     */
    @XNode("@bundle")
    protected String bundle;

    @XContent("documentation")
    protected String documentation;

    protected URL xmlFileUrl;

    /**
     * This is used by the component persistence service to identify registration that was dynamically created and
     * persisted by users.
     */
    protected boolean isPersistent;

    protected transient AbstractRuntimeContext context;

    // the managed component
    protected transient ComponentInstanceImpl component;

    public RegistrationInfoImpl() {
    }

    /**
     * Useful when dynamically registering components
     *
     * @param name the component name
     */
    public RegistrationInfoImpl(ComponentName name) {
        this.name = name;
    }

    /**
     * Attach to a manager - this method must be called after all registration fields are initialized.
     *
     * @param manager
     * @throws RuntimeServiceException
     */
    public void attach(ComponentManagerImpl manager) throws RuntimeServiceException {
        if (this.manager != null) {
            throw new IllegalStateException("Registration '" + name + "' was already attached to a manager");
        }
        this.manager = manager;
        computeNames();
        computePendings();
    }

    protected void computeNames() {
        names.add(name);
        if (aliases != null) {
            names.addAll(aliases);
        }
    }

    protected void computePendings() throws RuntimeServiceException {
        if (requires == null || requires.isEmpty()) {
            return;
        }
        // fill the requirements and pending map
        for (ComponentName otherName : requires) {
            RegistrationInfoImpl other = manager.getRegistrationInfo(otherName);
            if (other != null) {
                if (other.isResolved()) {
                    requiredResolved.add(other);
                } else {
                    requiredRegistered.add(other);
                }
                other.dependsOnMe.add(this);
            } else {
                requiredPendings.add(otherName);
            }
        }
    }

    public void setContext(AbstractRuntimeContext rc) {
        context = rc;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public final boolean isPersistent() {
        return isPersistent;
    }

    @Override
    public void setPersistent(boolean isPersistent) {
        this.isPersistent = isPersistent;
    }

    public final boolean isDisposed() {
        return manager == null;
    }

    @Override
    public ExtensionPoint[] getExtensionPoints() {
        return extensionPoints;
    }

    @Override
    public ComponentInstanceImpl getComponent() {
        return component;
    }

    /**
     * Reload the underlying component if reload is supported
     *
     * @throws Exception
     */
    public void reload() throws Exception {
        if (component != null) {
            component.reload();
        }
    }

    @Override
    public ComponentName getName() {
        return name;
    }

    @Override
    public Map<String, Property> getProperties() {
        return properties;
    }

    public ExtensionPointImpl getExtensionPoint(String name) {
        for (ExtensionPointImpl xp : extensionPoints) {
            if (xp.name.equals(name)) {
                return xp;
            }
        }
        return null;
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public Extension[] getExtensions() {
        return extensions;
    }

    @Override
    public Set<ComponentName> getAliases() {
        return aliases;
    }

    @Override
    public Set<ComponentName> getRequiredComponents() {
        return requires;
    }

    @Override
    public RuntimeContext getContext() {
        return context;
    }

    @Override
    public String getBundle() {
        return bundle;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public String getDocumentation() {
        return documentation;
    }

    @Override
    public String toString() {
        return "RegistrationInfo: " + name;
    }

    @Override
    public ComponentManager getManager() {
        return manager;
    }

    protected void register(Set<? extends RegistrationInfoImpl> dependsOnMe) throws RuntimeServiceException {
        if (state != UNREGISTERED) {
            throw new IllegalStateException("Component not in registered state" + this);
        }
        this.dependsOnMe.addAll(dependsOnMe);
        state = REGISTERED;
        handlePreRegistered();
        manager.sendEvent(new ComponentEvent(ComponentEvent.COMPONENT_REGISTERED, this));
        handleRegistered();
    }

    protected void handlePreRegistered() {
        for (RegistrationInfoImpl other : dependsOnMe) { // unaliased
            RegistrationInfoImpl otherImpl = other;
            otherImpl.requiredPendings.removeAll(names);
            otherImpl.requiredRegistered.add(this);
        }
    }

    protected void handleRegistered() throws RuntimeServiceException {
        if (requiredPendings.isEmpty() && requiredRegistered.isEmpty()) {
            resolve();
        }
    }

    void unregister() {
        if (state != REGISTERED) {
            throw new IllegalStateException("Component not in registered state" + this);
        }
        for (RegistrationInfoImpl other : requiredResolved) {
            other.dependsOnMe.remove(this);
        }
        state = UNREGISTERED;
        context.handleComponentUnregistered(this);
        manager.sendEvent(new ComponentEvent(ComponentEvent.COMPONENT_UNREGISTERED, this));
    }

    protected ComponentInstanceImpl createComponentInstance() throws RuntimeServiceException {
        return new ComponentInstanceImpl(this);
    }

    public void restart() throws Exception {
        deactivate();
        activate();
    }

    @Override
    public int getApplicationStartedOrder() {
        if (component == null) {
            return 0;
        }
        Object ci = component.getInstance();
        if (!(ci instanceof Component)) {
            return 0;
        }
        return ((Component) ci).getApplicationStartedOrder();
    }

    protected boolean appStarted;

    @Override
    public void notifyApplicationStarted() {
        if (state != RegistrationInfo.ACTIVATED) {
            return;
        }
        if (component == null) {
            return;
        }
        if (appStarted) {
            return;
        }
        appStarted = true;
        Object ci = component.getInstance();
        if (!(ci instanceof Component)) {
            return;
        }

        try {
            ((Component) ci).applicationStarted(component);
        } catch (RuntimeException e) {
            appStarted = false;
            try {
                deactivate();
            } catch (Exception cause) {
                e.addSuppressed(cause);
            }
            throw e;
        }

    }

    public boolean lazyActivate() {
        if (state != RESOLVED) {
            return isActivated();
        }
        try {
            activate();
        } catch (Exception cause) {
            throw new RuntimeServiceException("Cannot lazy activate " + bundle, cause);
        }
        return true;
    }

    @Override
    public void activate() throws RuntimeServiceException {
        if (state != RESOLVED) {
            throw new IllegalStateException("component not in resolved state (" + this + ")");
        }

        if (context.getState() != RuntimeContext.STARTING && context.getState() != RuntimeContext.ACTIVATED) {
            throw new IllegalStateException("context not in activating state (" + context + ")");
        }

        state = ACTIVATING;
        manager.sendEvent(new ComponentEvent(ComponentEvent.ACTIVATING_COMPONENT, this));

        handleActivating();

        state = ACTIVATED;
        manager.sendEvent(new ComponentEvent(ComponentEvent.COMPONENT_ACTIVATED, this));

        handleActivated();

    }

    protected void handleActivating() throws RuntimeServiceException {
        // check required component
        for (RegistrationInfoImpl other : requiredResolved) {
            if (context == other.context) {
                other.lazyActivate();
            } else if (other.state != ACTIVATED) {
                other.context.activate();
            }
        }


        component = createComponentInstance();

        component.activate();

        log.info("Component activated: " + name);

        manager.registerServices(this);

        Try<Void> monitor = TryCompanion.<Void> of(RuntimeException.class).empty();

        // register contributed extensions if any
        if (extensions != null) {
            monitor = monitor.forEachAndCollect(
                    Stream.of(extensions),
                    xt -> {
                        if (xt.getTargetComponent() == null) {
                            throw new RuntimeServiceException(
                                    "Bad extension declaration (no target attribute specified). Component: "
                                            + getName());
                        }
                        xt.setComponent(component);
                        manager.registerExtension(xt);

                    });
        }

        // register pending extensions if any
        Set<Extension> contributedExtensions = manager.extensionPendingsByComponent.remove(name);
        if (contributedExtensions != null) {
            monitor = monitor.forEachAndCollect(
                    contributedExtensions.stream(),
                    xt -> {
                        try {
                            manager.loadContributions(this, xt);
                            component.registerExtension(xt);
                        } catch (RuntimeException e) {
                            throw new RuntimeServiceException(
                                    "Failed to register extension to: " + xt.getTargetComponent() + ", xpoint: "
                                            + xt.getExtensionPoint() + " in component: " + xt.getComponent()
                                                    .getName(),
                                    e);
                        }
                    });
        }

        // throw errors if any
        monitor
                .orElseThrow(() -> new RuntimeServiceException("activating " + bundle));
    }

    protected void handleActivated() {
        context.handleComponentActivated(this);
    }

    public void deactivate() throws RuntimeServiceException {
        if (state != ACTIVATED) {
            return;
        }

        state = DEACTIVATING;

        TryCompanion.<Void> of(RuntimeException.class)
                .run(() -> manager.sendEvent(new ComponentEvent(ComponentEvent.DEACTIVATING_COMPONENT, this)))
                .run(() -> handleDeactivating())
                .run(() -> state = RESOLVED)
                .run(() -> manager.sendEvent(new ComponentEvent(ComponentEvent.COMPONENT_DEACTIVATED, this)))
                .run(() -> handleDeactivated())
                .orElseThrow(() -> new RuntimeServiceException("deactiving " + this));

    }

    protected void handleDeactivating() throws RuntimeServiceException {
        TryCompanion.<Void> of(RuntimeException.class)
                .forEachAndCollect(
                        dependsOnMe.stream(),
                        RegistrationInfoImpl::deactivate)
                .forEachAndCollect(
                        Stream.of(Optional
                                .ofNullable(extensions)
                                .orElseGet(() -> new ExtensionImpl[0])),
                        xt -> manager.unregisterExtension(xt))
                .run(() -> component.deactivate())
                .run(() -> component = null)
                .orElseThrow(() -> new RuntimeServiceException("deactivating " + this));
      }

    protected void handleDeactivated() {
        log.info("Component deactivated: " + name);
    }

    public void resolve() throws RuntimeServiceException {
        if (state != REGISTERED) {
            return;
        }

        state = RESOLVED;
        TryCompanion.<Void> of(RuntimeException.class).empty()
                .consume(self -> handleResolving(self))
                .run(() -> manager.sendEvent(new ComponentEvent(ComponentEvent.COMPONENT_RESOLVED, this)))
                .consume(self -> handleResolved(self))
                .orElseThrow(() -> new RuntimeServiceException("Resolving " + this));
    }

    protected Try<Void> handleResolving(Try<Void> monitor) {
        for (RegistrationInfoImpl other : dependsOnMe) {
            if (other.context != context) {
                continue;
            }
            other.requiredRegistered.remove(this);
            other.requiredResolved.add(this);
            if (other.requiredPendings.isEmpty() && other.requiredRegistered.isEmpty()) {
                monitor = monitor.run(() -> other.resolve());
            }
        }
        return monitor;
    }

    protected Try<Void> handleResolved(Try<Void> monitor) {
        for (RegistrationInfoImpl other : dependsOnMe) {
            if (other.context == context) {
                continue;
            }
            other.requiredRegistered.remove(this);
            other.requiredResolved.add(this);
            if (other.requiredPendings.isEmpty() && other.requiredRegistered.isEmpty()) {
                monitor.run(() -> other.resolve());
            }
        }
        context.handleComponentResolved(this);
        return monitor;
    }

    public void unresolve() {
        if (state == REGISTERED || state == UNREGISTERED) {
            return;
        }

        if (state == ACTIVATED) {
            deactivate();
        }

        handleUnresolving();

        state = REGISTERED;
        manager.sendEvent(new ComponentEvent(ComponentEvent.COMPONENT_UNRESOLVED, this));
    }

    void handleUnresolving() {
        try {
            for (RegistrationInfoImpl info : dependsOnMe) {
                info.unresolve();
            }
        } finally {
            manager.unregisterServices(this);
            context.handleComponentUnresolved(this);
        }
    }

    @Override
    public boolean isActivated() {
        return state >= ACTIVATED;
    }

    @Override
    public boolean isResolved() {
        return state >= RESOLVED;
    }

    @Override
    public String[] getProvidedServiceNames() {
        if (serviceDescriptor != null) {
            return serviceDescriptor.services;
        }
        return new String[0];
    }

    public ServiceDescriptor getServiceDescriptor() {
        return serviceDescriptor;
    }

    @Override
    public String getImplementation() {
        return implementation;
    }

    @Override
    public URL getXmlFileUrl() {
        return xmlFileUrl;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof RegistrationInfo) {
            return name.equals(((RegistrationInfo) obj).getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
