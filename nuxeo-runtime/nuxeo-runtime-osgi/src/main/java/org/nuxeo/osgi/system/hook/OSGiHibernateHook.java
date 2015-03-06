package org.nuxeo.osgi.system.hook;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.nuxeo.osgi.bootstrap.OSGiFile;
import org.nuxeo.osgi.bootstrap.OSGiHook;
import org.osgi.framework.Constants;

public class OSGiHibernateHook implements OSGiHook {
    @Override
    public Manifest onManifest(OSGiFile file, Manifest mf) {
        Attributes mains = mf.getMainAttributes();
        String name = mains.getValue(Constants.BUNDLE_SYMBOLICNAME);
        if (name == null) {
            return mf;
        }
        if (!name.contains("hibernate")) {
            return mf;
        }
        if (name.equals("org.hibernate.hibernate-core")) {
            return mf;
        }
        mains.putValue(Constants.FRAGMENT_HOST, "org.hibernate.hibernate-core");
        return mf;
    }

    @Override
    public OSGiFile onFile(OSGiFile file) {
        return file;
    }
}