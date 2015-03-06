package org.nuxeo.runtime.test.runner.osgi;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;

public class OSGiFeaturesRunner extends BlockJUnit4ClassRunner {

    public OSGiFeaturesRunner(Class<?> klass) throws InitializationError, ClassNotFoundException, InterruptedException {
        super(OSGiRunnerBridge.reloadClass(klass));
    }

    @Override
    public void run(RunNotifier notifier) {
        // run test in OSGi thread
        Bundle bundle = ((BundleReference) getTestClass().getJavaClass().getClassLoader()).getBundle();
        try {
            bundle.adapt(ExecutorService.class).submit(new Runnable() {

                @Override
                public void run() {
                    OSGiFeaturesRunner.super.run(notifier);
                }
            }).get();
        } catch (InterruptedException cause) {
            notifier.fireTestFailure(new Failure(getDescription(), cause));
            Thread.currentThread().interrupt();
        } catch (ExecutionException cause) {
            notifier.fireTestFailure(new Failure(getDescription(), cause));
        }
    }

}
