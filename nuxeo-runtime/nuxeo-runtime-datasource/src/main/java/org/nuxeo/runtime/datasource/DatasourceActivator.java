package org.nuxeo.runtime.datasource;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.nuxeo.common.collections.Streams;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.runtime.RuntimeServiceException;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class DatasourceActivator implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {
        DriverManager.getDrivers(); // load JDBC drivers
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        TryCompanion.<Void> of(SQLException.class)
                .sneakyForEachAndCollect(
                        Streams.of(DriverManager.getDrivers()),
                        DriverManager::deregisterDriver)
                .orElseThrow(() -> new RuntimeServiceException("unregistering jdbc drivers"));
    }

}
