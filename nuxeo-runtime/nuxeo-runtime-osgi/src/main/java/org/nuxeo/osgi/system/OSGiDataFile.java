package org.nuxeo.osgi.system;

import java.io.File;

import org.nuxeo.osgi.bootstrap.OSGiEnvironment;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;

public class OSGiDataFile {

    final OSGiSystem system;

    OSGiDataFile(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<Activation>() {

            @Override
            public Class<Activation> typeof() {
                return Activation.class;
            }

            @Override
            public Activation adapt(Bundle bundle) {
                return new Activation(bundle);
            }

        });
    }

    class Activation {
        final Bundle bundle;

        Activation(Bundle bundle) {
            this.bundle = bundle;
        }

        File resolve(String pathname) {
            return new File(bundle.adapt(OSGiEnvironment.class).dataDir, pathname);
        }
    }

}
