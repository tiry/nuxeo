package org.nuxeo.runtime.jtajca;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class NuxeoContainerActivator implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {
        ;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        NuxeoContainer.killupGeronimoTimer();
    }




}
