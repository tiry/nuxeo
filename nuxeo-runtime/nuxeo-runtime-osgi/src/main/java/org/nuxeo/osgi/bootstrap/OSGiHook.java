package org.nuxeo.osgi.bootstrap;

import java.util.jar.Manifest;

import org.osgi.framework.BundleException;

public interface OSGiHook {

    OSGiFile onFile(OSGiFile file) throws BundleException;

    Manifest onManifest(OSGiFile file, Manifest mf) throws BundleException;

}
