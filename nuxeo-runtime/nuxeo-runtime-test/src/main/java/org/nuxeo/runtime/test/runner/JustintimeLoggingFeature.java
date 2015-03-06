/*******************************************************************************
 * Copyright (c) 2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.nuxeo.runtime.test.runner;

import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.osgi.framework.BundleContext;

@Features(LoggingFeature.class)
public class JustintimeLoggingFeature extends SimpleFeature {

    boolean hasFailure;

    BundleContext context;

    RunListener listener;

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        listener = new RunListener() {
            @Override
            public void testFailure(Failure failure) throws Exception {
                hasFailure = true;
            }
        };
        runner.injector.getInstance(RunNotifier.class)
                .addListener(listener);
    }


    @Override
    public void start(FeaturesRunner runner) throws Exception {
//        LoggingConfigurator.Holder.SELF.justintime(LoggingConfigurator.Scope.APPLICATION_SCOPE);
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {
//        Assert.assertNotNull(listener);
//        runner.injector.getInstance(RunNotifier.class)
//                .removeListener(listener);
//        if (!hasFailure) {
//            LoggingConfigurator.Scope.APPLICATION_SCOPE.get()
//                    .forget(runner.getDescription()
//                            .getDisplayName() + " was sucessuful, forgeting logs");
//        } else {
//            LoggingConfigurator.Scope.APPLICATION_SCOPE.get()
//                    .commit();
//        }
    }
}
