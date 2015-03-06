package org.nuxeo.osgi.bootstrap;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public interface OSGiCaller {

    public interface Callable<T> {
        T call(Bundle bundle) throws BundleException;
    }

    public interface Runnable {
        void run(Bundle bundle) throws BundleException;
    }

    <T> T call(Callable<T> invokable) throws BundleException;

    void run(Runnable runnable) throws BundleException;

}
