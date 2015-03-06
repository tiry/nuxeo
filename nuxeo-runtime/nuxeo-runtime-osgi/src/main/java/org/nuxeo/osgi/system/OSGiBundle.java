/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and contributors.
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

package org.nuxeo.osgi.system;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.nuxeo.osgi.bootstrap.OSGiFile;
import org.nuxeo.osgi.system.OSGiRepository.Revision.TransientRepository.Metaspace;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleRevision;

public class OSGiBundle implements Bundle {

    final OSGiSystem system;

    OSGiBundle(OSGiSystem system) {
        this.system = system;
    }

    @Override
    public int getState() {
        return adapt(OSGiLifecycle.StateMachine.class).current.toOSGi();
    }

    @Override
    public String getLocation() {
        return adapt(OSGiFile.class).getLocation().toASCIIString();
    }

    @Override
    public URL getEntry(String pathname) {
        return OSGiFile.toURL.apply(adapt(OSGiFile.class).getEntry(pathname));
    }

    @Override
    public Enumeration<URL> findEntries(String pathname, String pattern, boolean recurse) {
        return OSGiFile.toEnums(adapt(OSGiFile.class).findEntries(pathname, pattern, recurse), OSGiFile.toURL);
    }

    @Override
    public Enumeration<String> getEntryPaths(String pathname) {
        return OSGiFile.toEnums(adapt(OSGiFile.class).getEntryPaths(pathname), OSGiFile.toString);
    }

    @Override
    public long getBundleId() {
        return adapt(OSGiRepository.Revision.class).id;
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return adapt(Metaspace.class).headers;
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLastModified() {
        return adapt(OSGiLifecycle.StateMachine.class).lastmodified();
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceReference<?>[] getServicesInUse() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSymbolicName() {
        return adapt(BundleRevision.class).getSymbolicName();
    }

    @Override
    public boolean hasPermission(Object permission) {
        return true;
    }

    @Override
    public void uninstall() throws BundleException {
        adapt(OSGiLifecycle.Transitions.class).uninstall();
    }

    @Override
    public void update() throws BundleException {
        throw new UnsupportedOperationException("Bundle.update() operations was not yet implemented");
    }

    @Override
    public void update(InputStream in) throws BundleException {
        throw new UnsupportedOperationException("Bundle.update() operations was not yet implemented");
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compareTo(Bundle o) {
        return adapt(Comparable.class).compareTo(o.adapt(Comparable.class));
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Version getVersion() {
        return adapt(BundleRevision.class).getVersion();
    }

    @Override
    public File getDataFile(String filename) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start(int options) throws BundleException {
        adapt(OSGiLifecycle.Transitions.class).start(options);
    }

    @Override
    public void start() throws BundleException {
        adapt(OSGiLifecycle.Transitions.class).start(0);
    }

    @Override
    public void stop(int options) throws BundleException {
        adapt(OSGiLifecycle.Transitions.class).stop(options);
    }

    @Override
    public void stop() throws BundleException {
        adapt(OSGiLifecycle.Transitions.class).stop(0);
    }

    @Override
    public URL getResource(String name) {
        return adapt(ClassLoader.class).getResource(name);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return adapt(ClassLoader.class).loadClass(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return adapt(ClassLoader.class).getResources(name);
    }

    @Override
    public BundleContext getBundleContext() {
        return adapt(BundleContext.class);
    }

    @Override
    public <A> A adapt(Class<A> type) {
        try {
            return system.adapt(type, this);
        } catch (final BundleException cause) {
            throw new UnsupportedOperationException("Cannot adapt " + this + " onto " + type, cause);
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder()
                .append("Bundle@" + Integer.toHexString(hashCode()));
        // protect parameters extraction (if not installed)
        try {
            builder.append(
                    new StringBuilder()
                            .append("[")
                            .append("name=" + getSymbolicName() + ",")
                            .append("state=" + adapt(OSGiLifecycle.StateMachine.class).current)
                            .append("]"));
        } catch (final Exception cause) {
            ;
        }
        return builder.toString();
    }

}
