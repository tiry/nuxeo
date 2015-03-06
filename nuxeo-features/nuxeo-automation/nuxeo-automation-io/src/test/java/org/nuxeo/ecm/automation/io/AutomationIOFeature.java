package org.nuxeo.ecm.automation.io;

import org.nuxeo.ecm.webengine.test.WebEngineFeatureCore;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.SimpleFeature;

@Features(WebEngineFeatureCore.class)
@Deploy({ "org.nuxeo.ecm.automation.core", "org.nuxeo.ecm.automation.io" })
public class AutomationIOFeature extends SimpleFeature {

}
