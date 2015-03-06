package org.nuxeo.runtime.logging.logback;

import org.nuxeo.common.Environment;
import org.nuxeo.common.LoaderConstants;

import ch.qos.logback.core.PropertyDefinerBase;

public class LogbackAppNameProvider extends PropertyDefinerBase {

    @Override
    public String getPropertyValue() {
        return Environment.getDefault().getProperty(LoaderConstants.APP_NAME);
    }

}
