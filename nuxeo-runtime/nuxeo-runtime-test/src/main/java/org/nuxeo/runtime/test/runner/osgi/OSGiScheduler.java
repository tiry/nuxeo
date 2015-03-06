package org.nuxeo.runtime.test.runner.osgi;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.junit.runners.model.RunnerScheduler;
import org.osgi.framework.launch.Framework;

public class OSGiScheduler implements RunnerScheduler {

    final List<Future<?>> scheduled = new LinkedList<Future<?>>();

    @Override
    public void schedule(final Runnable statement) {
        Framework framework = OSGiRunnerBridge.self.take();
        scheduled.add(framework.adapt(ExecutorService.class).submit(new Runnable() {

            @Override
            public void run() {
                try {
                    statement.run();
                } finally {
                    OSGiRunnerBridge.self.recycle(framework);
                }
            }
        }));
    }

    @Override
    public void finished() {
        AssertionError errors = new AssertionError("junit statement should return");
        scheduled.stream().forEach(future -> {
            try {
                future.get();
            } catch (ExecutionException cause) {
                errors.addSuppressed(cause);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
            }
            return;
        });
        if (errors.getSuppressed().length > 0) {
            throw errors;
        }
        return;
    }
}
