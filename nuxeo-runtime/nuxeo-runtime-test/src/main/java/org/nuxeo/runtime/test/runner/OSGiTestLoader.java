/*******************************************************************************
 * Copyright (c) 2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.nuxeo.runtime.test.runner;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Properties;

import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.nuxeo.osgi.bootstrap.OSGiBootstrap;
import org.nuxeo.osgi.bootstrap.OSGiCaller;
import org.nuxeo.runtime.test.runner.osgi.OSGiRunnerBridge;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

public class OSGiTestLoader {

    final String name;

    public OSGiTestLoader(String name) {
        this.name = name;
    }

    String newOSGiTempFile() {
        try {
            final File tempfile = File.createTempFile("nxosgi", null, new File("target"));
            tempfile.delete();
            tempfile.mkdirs();
            return tempfile.toString();
        } catch (IOException e) {
            throw new Error("Cannot create temp osgi file", e);
        }
    }

    void reloadAndRun(FeaturesRunner runner, RunNotifier notifier) {
        Description description = runner.getDescription();
        Properties properties = runner.getProperties();

        AssertionError errors = new AssertionError("Errors while running " + description.getDisplayName());

        Framework framework = OSGiRunnerBridge.self.take();
        ClassLoader tcl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(framework.adapt(ClassLoader.class));
            try {
                // reload and run test
                Class<? extends BlockJUnit4ClassRunner> osgiClazz = reloadClass(framework, FeaturesRunner.class);
                Class<?> targetClass = reloadClass(framework, description.getTestClass());
                MethodHandle handle = MethodHandles.publicLookup().findConstructor(osgiClazz,
                        MethodType.methodType(void.class, Class.class, Properties.class));
                BlockJUnit4ClassRunner osgiRunner = osgiClazz.cast(handle.invoke(targetClass, properties));
                osgiRunner.filter(new Filter() {

                    @Override
                    public boolean shouldRun(Description child) {
                        return description.getChildren().contains(child);
                    }

                    @Override
                    public String describe() {
                        return "OSGi Test Loader Matcher : " + description.getChildren();
                    }

                });
                if (runner.otherClass != null) {
                    Field field = osgiClazz.getDeclaredField("otherClass");
                    field.setAccessible(true);
                    field.set(osgiRunner, reloadClass(framework, runner.otherClass));
                }
                framework.adapt(OSGiCaller.class).run(new OSGiCaller.Runnable() {

                    @Override
                    public void run(Bundle bundle) {
                        osgiRunner.run(notifier);
                    }
                });
            } catch (Throwable error) {
                errors.addSuppressed(error);
            } finally {
                Thread.currentThread().setContextClassLoader(tcl);
            }
        } finally {
            OSGiRunnerBridge.self.recycle(framework);
        }
        if (errors.getSuppressed().length > 0) {
            throw errors;
        }
    }

    <T> Class<T> reloadClass(Framework framework, Class<T> clazz) {
        try {
            return framework.adapt(OSGiBootstrap.class).reloadClass(clazz);
        } catch (ClassNotFoundException | BundleException cause) {
            throw new AssertionError("Cannot reload in OSGi " + clazz, cause);
        }
    }

}
