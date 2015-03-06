/*
 * Copyright (c) 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.runtime.model.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.common.codec.CryptoProperties;
import org.nuxeo.common.logging.JavaUtilLoggingHelper;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.common.utils.TextTemplate;
import org.nuxeo.common.utils.Vars;
import org.nuxeo.runtime.RuntimeExtension;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.RuntimeServiceEvent;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.nuxeo.runtime.model.RuntimeContext;
import org.osgi.framework.Bundle;

/**
 * Abstract implementation of the Runtime Service.
 * <p>
 * Implementors are encouraged to extend this class instead of directly implementing the {@link RuntimeService}
 * interface.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public abstract class AbstractRuntimeService implements RuntimeService {

    private static final Log log = LogFactory.getLog(RuntimeService.class);

    protected boolean isStarted = false;

    protected boolean isShuttingDown = false;

    protected File workingDir;

    protected final List<URL> registeredProperties = new LinkedList<>();

    protected CryptoProperties properties;

    protected final ComponentManagerImpl manager;

    protected final AbstractRuntimeContext runtimeContext;

    protected final Map<String, AbstractRuntimeContext> contextsByName = new HashMap<>();

    protected final List<RuntimeExtension> extensions = new ArrayList<>();

    protected AbstractRuntimeService(AbstractRuntimeContext context) {
        this(context, context.getBundle().adapt(Properties.class));
    }

    // warnings during the deployment. Here are collected all errors occurred
    // during the startup
    protected final List<String> warnings = new ArrayList<>();

    protected AbstractRuntimeService(AbstractRuntimeContext context, Properties env) {
        runtimeContext = context;
        properties = new CryptoProperties(env);
        manager = createComponentManager();
    }

    @Override
    public List<String> getWarnings() {
        return warnings;
    }

    protected ComponentManagerImpl createComponentManager() {
        return new ComponentManagerImpl(this);
    }

    protected static URL getBuiltinFeatureURL() {
        return Thread.currentThread().getContextClassLoader().getResource("org/nuxeo/runtime/nx-feature.xml");
    }

    static final String NL = System.lineSeparator();

    @Override
    public void start() {
        StringBuilder sb = new StringBuilder();
        getConfigSummary(sb);
        log.info("Starting Nuxeo Runtime service " + getName() + "; version: " + getVersion() + NL + sb.toString());
        // NXRuntime.setInstance(this);

        Framework.sendEvent(new RuntimeServiceEvent(RuntimeServiceEvent.RUNTIME_ABOUT_TO_START, this));
        doStart();
        startExtensions();
        isStarted = true;
        Framework.sendEvent(new RuntimeServiceEvent(RuntimeServiceEvent.RUNTIME_STARTED, this));
    }

    @Override
    public void stop() {
        log.info("Stopping Nuxeo Runtime service " + getName() + "; version: " + getVersion());
        isStarted = false;
        TryCompanion.<Void> of(RuntimeException.class)
                .run(() -> isShuttingDown = true)
                .run(() -> Framework
                        .sendEvent(new RuntimeServiceEvent(RuntimeServiceEvent.RUNTIME_ABOUT_TO_STOP, this)))
                .run(() -> stopExtensions())
                .run(() -> doStop())
                .run(() -> Framework.sendEvent(new RuntimeServiceEvent(RuntimeServiceEvent.RUNTIME_STOPPED, this)))
                .run(() -> manager.shutdown())
                .run(() -> JavaUtilLoggingHelper.reset())
                .run(() -> isShuttingDown = false)
                .orElseThrow(() -> new RuntimeServiceException(
                        "Stopping Nuxeo Runtime service " + getName() + "; version: " + getVersion()));
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    protected void doStart() {
    }

    protected void doStop() {
    }

    @Override
    public File getHome() {
        return workingDir;
    }

    public void setHome(File home) {
        workingDir = home;
    }

    @Override
    public String getDescription() {
        return toString();
    }

    @Override
    public CryptoProperties getProperties() {
        // do not unreference properties: some methods rely on this to set
        // variables here...
        return properties;
    }

    @Override
    public void setProperties(Properties properties) {
        properties.putAll(properties);
    }

    @Override
    public String getProperty(String name) {
        return getProperty(name, null);
    }

    @Override
    public String getProperty(String name, String defValue) {
        String value = properties.getProperty(name, defValue);
        if (value == null || ("${" + name + "}").equals(value)) {
            // avoid loop, don't expand
            return value;
        }
        return expandVars(value);
    }

    @Override
    public void setProperty(String name, Object value) {
        properties.setProperty(name, value.toString());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb.append(getName()).append(" version ").append(getVersion().toString()).toString();
    }

    @Override
    public Object getComponent(String name) {
        ComponentInstance co = getComponentInstance(name);
        return co != null ? co.getInstance() : null;
    }

    @Override
    public Object getComponent(ComponentName name) {
        ComponentInstance co = getComponentInstance(name);
        return co != null ? co.getInstance() : null;
    }

    @Override
    public ComponentInstance getComponentInstance(String name) {
        return manager.getComponent(new ComponentName(name));
    }

    @Override
    public ComponentInstance getComponentInstance(ComponentName name) {
        return manager.getComponent(name);
    }

    @Override
    public ComponentManager getComponentManager() {
        return manager;
    }

    @Override
    public RuntimeContext getContext() {
        return runtimeContext;
    }

    @Override
    public AbstractRuntimeContext getContext(String name) {
        return contextsByName.get(name);
    }

    protected void startExtensions() {
        TryCompanion.<Void> of(RuntimeException.class)
                .forEachAndCollect(extensions.stream(), RuntimeExtension::start)
                .orElseThrow(() -> new RuntimeServiceException("Starting runtime extensions"));
    }

    protected void stopExtensions() {
        TryCompanion.<Void> of(RuntimeException.class)
                .forEachAndCollect(extensions.stream(), RuntimeExtension::stop)
                .orElseThrow(() -> new RuntimeServiceException("Starting runtime extensions"));
    }

    @Override
    public <T> T getService(Class<T> serviceClass) {
        return manager.getService(serviceClass);
    }

    @Override
    public String expandVars(String expression) {
        return new TextTemplate(properties).processText(expression);
    }

    @Override
    public File getBundleFile(Bundle bundle) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Bundle getBundle(String symbolicName) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * @since 5.5
     * @param msg summary message about all components loading status
     * @return true if there was no detected error, else return false
     */
    @Override
    public boolean getStatusMessage(StringBuilder msg) {
        String hr = "======================================================================";
        if (!warnings.isEmpty()) {
            msg.append(hr).append("\n= Component Loading Errors:\n");
            for (String warning : warnings) {
                msg.append("  * ").append(warning).append('\n');
            }
        }
        Map<ComponentName, Set<RegistrationInfo>> pendingRegistrations = manager.getPendingRegistrations();
        Collection<ComponentName> activatingRegistrations = manager.getActivatingRegistrations();
        msg.append(hr)
                .append("\n= Component Loading Status: Pending: ")
                .append(pendingRegistrations.size())
                .append(" / Unstarted: ")
                .append(activatingRegistrations.size())
                .append(" / Total: ")
                .append(manager.getRegistrations().size())
                .append('\n');
        for (Entry<ComponentName, Set<RegistrationInfo>> e : pendingRegistrations.entrySet()) {
            msg.append("  * ").append(e.getValue()).append(" requires ").append(e.getKey()).append('\n');
        }
        msg.append(hr);
        return (warnings.isEmpty() && pendingRegistrations.isEmpty());
    }

    @Override
    public void getConfigSummary(StringBuilder msg) {
        Environment env = Environment.getDefault();
        String newline = NL;
        String hr = newline + "======================================================================" + newline;
        msg.append("  * Server home = " + env.getServerHome() + newline);
        msg.append("  * Runtime home = " + env.getRuntimeHome() + newline);
        msg.append("  * Data Directory = " + env.getData() + newline);
        msg.append("  * Log Directory = " + env.getLog() + newline);
        msg.append("  * Configuration Directory = " + env.getConfig() + newline);
        msg.append("  * Temp Directory = " + env.getTemp() + newline);
        msg.append(hr);
    }

    protected void loadBlacklist(URL url) {
        try (InputStream in = url.openStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            reader.lines().forEach(line -> manager.blacklist.add(line));
        } catch (IOException cause) {
            throw new RuntimeServiceException("Cannot load " + url, cause);
        }
    }

    @Override
    public void reloadProperties() {
        properties.clear();
        registeredProperties.stream().forEach(u -> loadProperties(u));
    }

    protected void loadProperties(URL url) {
        try (InputStream in = url.openStream()) {
            loadProperties(in);
        } catch (IOException cause) {
            throw new RuntimeServiceException("Cannot load " + url, cause);
        }
        registeredProperties.add(url);
    }

    protected void loadProperties(InputStream in) throws IOException {
        Properties props = new Properties();
        props.load(in);
        for (Entry<Object, Object> prop : props.entrySet()) {
            properties.put(prop.getKey().toString(), Vars.expand(prop.getValue().toString(), properties));
        }
    }

}
