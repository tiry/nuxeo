package org.nuxeo.osgi.system.hook;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.nuxeo.osgi.bootstrap.OSGiFile;
import org.nuxeo.osgi.bootstrap.OSGiHook;
import org.osgi.framework.Constants;

public class OSGiDynamicHook implements OSGiHook {

    @Override
    public OSGiFile onFile(OSGiFile file) {
        return file;
    }

    @Override
    public Manifest onManifest(OSGiFile file, Manifest mf) {
        Attributes attributes = mf.getMainAttributes();
        attributes.put(new Attributes.Name(Constants.BUNDLE_ACTIVATIONPOLICY), Constants.ACTIVATION_LAZY);
        attributes.put(new Attributes.Name(Constants.DYNAMICIMPORT_PACKAGE), "*");
        return mf;
    }

}
