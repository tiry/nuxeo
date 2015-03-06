package org.nuxeo.osgi.bootstrap;

import java.util.ServiceLoader;

import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;

public class OSGiSecurityManager extends SecurityManager {

    static final OSGiSecurityManager self = new OSGiSecurityManager();

    @Override
    protected Class<?>[] getClassContext() {
        return super.getClassContext();
    }

    boolean isService() {
        Class<?>[] classStack = getClassContext();
        for (Class<?> type : classStack) {
            if (ServiceLoader.class == type.getEnclosingClass()) {
                return true;
            }
        }
        return false;
    }

    Framework lookupFramework() {
        Class<?>[] classStack = getClassContext();
        for (Class<?> type : classStack) {
            ClassLoader loader = type.getClassLoader();
            if (!(loader instanceof OSGiClassLoader)) {
                continue;
            }
            Bundle bundle = ((OSGiClassLoader) loader).getBundle();
            if (bundle == null) {
                return null;
            }
            return bundle.adapt(Framework.class);
        }
        return null;
    }
}
