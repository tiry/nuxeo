package org.nuxeo.runtime.deployment.preprocessor;

import java.io.File;

import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.common.LoaderConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

public class DeploymentActivator implements BundleActivator, BundleTrackerCustomizer<Object> {

    protected static DeploymentActivator me;

    protected DeploymentPreprocessor preprocessor;

    @Override
    public void start(BundleContext context) throws Exception {
        me = this;
        File home = Environment.getDefault().getRuntimeHome();
        preprocessor = new DeploymentPreprocessor(home);
        preprocessor.init();
        String v = context.getProperty(LoaderConstants.PREPROCESSING);
        if (v != null) {
            if (!Boolean.parseBoolean(v)) {
                return;
            }
        }
        if (preprocessor.getRootContainer() != null) {
            new BundleTracker<>(context, Bundle.INSTALLED, this).open();
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        preprocessor = null;
        me = null;
    }

    @Override
    public Object addingBundle(Bundle bundle, BundleEvent event) {
        try {
            preprocessor.processBundle(bundle);
        } catch (Exception e) {
            LogFactory.getLog(DeploymentActivator.class).error("Cannot preprocess fragment from " + bundle, e);
        }
        return bundle;
    }

    @Override
    public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
        ;
    }

    @Override
    public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
        preprocessor.forgetBundle(bundle);
    }

}
