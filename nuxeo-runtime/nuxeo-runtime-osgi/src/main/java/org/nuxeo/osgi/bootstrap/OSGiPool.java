package org.nuxeo.osgi.bootstrap;

import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

public class OSGiPool {

    final BlockingQueue<Framework> pool = new LinkedBlockingQueue<Framework>();

    final Properties config;

    final Listener listener;

    public interface Listener {
        default void handleNew(Framework framework) throws BundleException {
        }

        default void handleAcquired(Framework framework) throws BundleException {
            framework.start();
        }

        default void handleRecycled(Framework framework) throws BundleException {
            framework.stop();
        }
    }

    public OSGiPool(Properties config, Listener listener) {
        this.config = config;
        this.listener = listener;
    }

    Framework create() throws BundleException {
        Framework framework = new OSGiBootstrap().boot(config);
        framework.adapt(OSGiCaller.class).run(new OSGiCaller.Runnable() {

            @Override
            public void run(Bundle bundle) throws BundleException {
                listener.handleNew(framework);
            }
        });
        return framework;
    }

    public void shutdown() throws InterruptedException, BundleException {
        OSGiMultiExceptionHandler errorsHandler = new OSGiMultiExceptionHandler("shutdown osgi pool");
        while (pool.isEmpty() == false) {
            try {
                pool.take().stop();
            } catch (BundleException error) {
                errorsHandler.add(error);
            }
        }
        errorsHandler.ifExceptionThrow();
    }

    public Framework take() throws InterruptedException, BundleException {
        Framework framework = pool.poll();
        if (framework == null) {
            framework = create();
        }
        listener.handleAcquired(framework);
        return framework;
    }

    public void recycle(Framework framework) throws BundleException {
        listener.handleRecycled(framework);
        pool.offer(framework);
    }

}
