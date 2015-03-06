package org.nuxeo.ecm.platform.ui.web.jsf;

import org.nuxeo.ecm.platform.audit.AuditFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.SimpleFeature;

@Features({ AuditFeature.class, PlatformFeature.class })
@Deploy({ //
        "org.nuxeo.ecm.core.persistence", //
        "org.nuxeo.ecm.platform.audit", //
        "org.nuxeo.ecm.actions", //
        "org.nuxeo.ecm.automation.core", //
        "org.nuxeo.ecm.user.invite", //
        "org.nuxeo.ecm.platform.web.common", //
        "org.nuxeo.web.resources.api", //
        "org.nuxeo.web.resources.core", //
        "org.nuxeo.ecm.platform.ui" })
public class WebFeature extends SimpleFeature {
//    "org.nuxeo.ecm.platform.forms.layout.core", //
//    "org.nuxeo.ecm.platform.forms.layout.client", //
//    "org.nuxeo.ecm.platform.contentview.jsf", //
}
