package org.nuxeo.ecm.core;

import org.nuxeo.runtime.test.NXRuntimeTestCase;
import org.nuxeo.runtime.test.runner.Deploy;

@Deploy({ "org.nuxeo.ecm.core.schema", //
    "org.nuxeo.ecm.core.query", //
    "org.nuxeo.ecm.core.api", //
    "org.nuxeo.ecm.core.event", //
    "org.nuxeo.ecm.core"})
public abstract class CoreTestCase extends NXRuntimeTestCase {

}
