/*
 * (C) Copyright 2006-2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     tdelprat, jcarsique
 */

package org.nuxeo.ecm.admin.runtime;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.PropertyResourceBundle;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.osgi.framework.Bundle;

/**
 * Extracts information about the Bundles currently deployed in Nuxeo Runtime
 *
 * @author tiry
 */
public class RuntimeInstrospection {

    protected static final Log log = LogFactory.getLog(RuntimeInstrospection.class);

    protected static SimplifiedServerInfo info;

    protected static List<String> bundleIds;

    public static synchronized SimplifiedServerInfo getInfo() {
        if (info == null) {
            RuntimeService runtime = Framework.getRuntime();
            Collection<RegistrationInfo> registrations = runtime.getComponentManager().getRegistrations();
            bundleIds = new ArrayList<>();
            List<SimplifiedBundleInfo> bundles = new ArrayList<>();
            for (RegistrationInfo ri : registrations) {
                Bundle bundle = ri.getContext().getBundle();
                if (bundle != null && !bundleIds.contains(bundle.getSymbolicName())) {
                    SimplifiedBundleInfo bi = getBundleSimplifiedInfo(bundle);
                    bundleIds.add(bundle.getSymbolicName());
                    if (bi != null) {
                        bundles.add(bi);
                    }
                }
            }
            Collections.sort(bundles);
            info = new SimplifiedServerInfo();
            info.setBundleInfos(bundles);
            info.setRuntimeVersion(runtime.getVersion().toString());
            info.setWarnings(runtime.getWarnings());
        }
        return info;
    }

    public static List<String> getBundleIds() {
        if (bundleIds == null) {
            getInfo();
        }
        return bundleIds;
    }

    protected static SimplifiedBundleInfo getBundleSimplifiedInfo(Bundle bundle) {
        try {
            Enumeration<URL> props = bundle.findEntries("META-INF", "pom.properties", true);
            if (!props.hasMoreElements()) {
                throw new FileNotFoundException();
            }
            try (InputStream is = props.nextElement().openStream()) {
                return new SimplifiedBundleInfo(bundle.getSymbolicName(),
                        new PropertyResourceBundle(is).getString("version"));
            }
        } catch (IOException cause) {
            // Fall back on the MANIFEST Bundle-Version
            org.osgi.framework.Version version = bundle.getVersion();
            return new SimplifiedBundleInfo(bundle.getSymbolicName(), version.toString());
        }
    }
}
