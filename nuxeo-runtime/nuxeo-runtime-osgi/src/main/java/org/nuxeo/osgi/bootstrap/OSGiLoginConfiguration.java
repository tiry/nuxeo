package org.nuxeo.osgi.bootstrap;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

import org.osgi.framework.launch.Framework;

class OSGiLoginConfiguration extends Configuration {

    final Configuration parent;

    OSGiLoginConfiguration() {
        parent = Configuration.getConfiguration();
    }

    void install() {
        Configuration.setConfiguration(this);
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        Framework system = OSGiSecurityManager.self.lookupFramework();
        if (system != null) {
            AppConfigurationEntry[] entries = system.adapt(Configuration.class).getAppConfigurationEntry(name);
            if (entries != null) {
                return entries;
            }
        }
        return parent.getAppConfigurationEntry(name);
    }

}
