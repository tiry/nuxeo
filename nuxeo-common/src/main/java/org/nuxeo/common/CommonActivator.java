package org.nuxeo.common;

import java.io.File;
import java.util.Properties;
import java.util.logging.Level;

import org.nuxeo.common.logging.JavaUtilLoggingHelper;
import org.nuxeo.common.utils.URLStreamHandlerFactoryInstaller;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class CommonActivator implements BundleActivator {

    /**
     * Property that controls whether or not to redirect JUL to JCL. By default is true (JUL will be redirected)
     */
    public static final String REDIRECT_JUL = "org.nuxeo.redirectJUL";

    public static final String REDIRECT_JUL_THRESHOLD = "org.nuxeo.rredirectJUL.threshold";

    @Override
    public void start(BundleContext context) throws Exception {
        Properties props = context.getBundle().adapt(Properties.class);
        String homedir = props.getProperty(LoaderConstants.HOME_DIR, context.getDataFile("/").toString());
        Environment env = new Environment(new File(homedir), props);
        Environment.setDefault(env);

        if (Boolean.parseBoolean(env.getProperty(REDIRECT_JUL, "false"))) {
            Level threshold = Level.parse(env.getProperty(REDIRECT_JUL_THRESHOLD, "INFO").toUpperCase());
            JavaUtilLoggingHelper.redirectToApacheCommons(threshold);
        }
        return;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        URLStreamHandlerFactoryInstaller.resetURLStreamHandlers();
        JavaUtilLoggingHelper.reset();
    }

}
