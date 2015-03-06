package org.nuxeo.runtime.deployment.preprocessor;

import java.io.IOException;

import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

public class DeploymentComponent extends DefaultComponent {

    @Override
    public void applicationStarted(ComponentContext context) {
        try {
            DeploymentActivator.me.preprocessor.predeploy();
        } catch (IOException cause) {
           throw new RuntimeException("Cannot preprocess bundles", cause);
        }
    }

}
