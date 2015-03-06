package org.nuxeo.osgi.bootstrap;

import java.io.IOException;
import java.net.URL;
import java.util.stream.Stream;

import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;

public interface OSGiWireContext extends BundleReference {

    /**
     * Find class in wired bundles. Returns null if the class is not found.
     */
    Class<?> findWiredClass(String classname) throws BundleException, IOException;

    /**
     * Get resource in wired bundles. Returns null is the resource is not found.
     */
    URL getWiredResource(String pathname) throws BundleException;

    /**
     * Search all resources in wired bundles.
     */
    Stream<URL> findWiredResources(String pathname) throws BundleException, IOException;

}
