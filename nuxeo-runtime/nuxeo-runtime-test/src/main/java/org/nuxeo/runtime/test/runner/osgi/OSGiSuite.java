package org.nuxeo.runtime.test.runner.osgi;

import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;

public class OSGiSuite extends Suite {

    public OSGiSuite(Class<?> clazz) throws InitializationError {
        this(new Class<?>[] { clazz });
    }

    public OSGiSuite(Class<?>[] suiteClasses) throws InitializationError {
        super(OSGiSuite.class, suiteClasses);
        setScheduler(new OSGiScheduler());
    }

}