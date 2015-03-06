package org.nuxeo.runtime.test.runner;

import org.nuxeo.runtime.logging.logback.LogbackConfigurator;

@Deploy({ "org.nuxeo.runtime.logging", "org.nuxeo.runtime.logging.jcl", "org.nuxeo.runtime.logging.logback", })
public class LoggingFeature extends SimpleFeature {

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        LogbackConfigurator.class.getClassLoader(); // force logback activation
    }
}
