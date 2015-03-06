package org.nuxeo.runtime.test.runner.osgi;

import java.util.Properties;

import org.nuxeo.common.LoaderConstants;
import org.nuxeo.osgi.bootstrap.OSGiPool;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;
import org.osgi.framework.launch.Framework;

public class OSGiRunnerBridge {

    public static OSGiRunnerBridge self = new OSGiRunnerBridge();

    final OSGiPool frameworks = initPool();

    class Setup implements OSGiPool.Listener {

     }

    OSGiPool initPool() {
        Properties env = new Properties(System.getProperties());
        // env.put(LoaderConstants.HOME_DIR, newOSGiTempFile());
        env.put(LoaderConstants.OSGI_BOOT_DELEGATION, "junit,org.junit,org.hamcrest");
        env.put("org.nuxeo.runtime.testing", "true");
        return new OSGiPool(env, new Setup());
    }

    public Framework take() {
        try {
            return frameworks.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted, cannot take framework", e);
        } catch (BundleException e) {
            throw new AssertionError("Errors, cannot take framework", e);
        }
    }

    public void recycle(Framework framework) {
        try {
            frameworks.recycle(framework);
        } catch (BundleException e) {
            throw new AssertionError("Errors, cannot recycle framework", e);
        }
    }

    static Class<?> reloadClass(Class<?> clazz) throws ClassNotFoundException {
        if (clazz.getClassLoader() instanceof BundleReference) {
            return clazz;
        }
        return self.take().loadClass(clazz.getName());
    }

}
