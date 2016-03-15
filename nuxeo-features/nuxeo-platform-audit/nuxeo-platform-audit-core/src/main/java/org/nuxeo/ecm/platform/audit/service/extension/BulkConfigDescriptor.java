package org.nuxeo.ecm.platform.audit.service.extension;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

@XObject("bulk")
public class BulkConfigDescriptor {

    @XNode("timeout")
    public int timeout = 10;

    @XNode("size")
    public int size = 1000;
}
