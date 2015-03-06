package org.nuxeo.ecm.automation.core.test;

import org.junit.runner.RunWith;
import org.nuxeo.ecm.webengine.test.WebEngineFeatureCore;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(WebEngineFeatureCore.class)
@Deploy({ "org.nuxeo.ecm.automation.core", "org.nuxeo.ecm.automation.io", "org.nuxeo.ecm.automation.features",
        "org.nuxeo.ecm.automation.server" })
public abstract class AutomationFeaturesTestCase {

}
