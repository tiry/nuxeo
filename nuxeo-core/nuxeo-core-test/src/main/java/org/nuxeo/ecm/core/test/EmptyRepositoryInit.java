package org.nuxeo.ecm.core.test;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.test.annotations.RepositoryInit;

public class EmptyRepositoryInit implements RepositoryInit {

    @Override
    public void populate(CoreSession session) {
        ;
    }

}
