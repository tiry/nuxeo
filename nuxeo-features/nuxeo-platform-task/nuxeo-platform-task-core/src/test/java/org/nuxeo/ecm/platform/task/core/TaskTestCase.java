package org.nuxeo.ecm.platform.task.core;

import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy({ "org.nuxeo.ecm.platform.content.template", "org.nuxeo.ecm.platform.task.core",
        "org.nuxeo.ecm.platform.task.testing" })
public abstract class TaskTestCase {

}
