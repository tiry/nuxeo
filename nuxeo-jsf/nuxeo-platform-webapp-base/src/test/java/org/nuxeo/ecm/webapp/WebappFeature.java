package org.nuxeo.ecm.webapp;

import org.nuxeo.ecm.platform.ui.web.jsf.WebFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.test.runner.SimpleFeature;

@Features({ WebFeature.class })
@Deploy({ //
        "org.nuxeo.ecm.platform.forms.layout.core", //
        "org.nuxeo.ecm.platform.forms.layout.client", //
        "org.nuxeo.ecm.platform.contentview.jsf", //
        "org.nuxeo.ecm.webapp.base"})
@LocalDeploy("org.nuxeo.ecm.webapp.base:disable-validation.xml")
public class WebappFeature extends SimpleFeature {

}
