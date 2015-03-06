/*
 * (C) Copyright 2006-2009 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.core.api.blobholder;

import javax.inject.Inject;

import org.junit.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.NXRuntimeTestCase;
import org.nuxeo.runtime.test.runner.Features;

@Features(CoreFeature.class)
public class TestBlobHolderAdapterService extends NXRuntimeTestCase {

    @Inject BlobHolderAdapterService adapters;

    @Test
    public void testContrib() throws Exception {
        assertEquals(0, BlobHolderAdapterComponent.getFactoryNames().size());
        deployTestContrib("org.nuxeo.ecm.core.test", "test-blob-holder-adapters-contrib.xml");
        assertEquals(1, BlobHolderAdapterComponent.getFactoryNames().size());


        DocumentModel doc = new DocumentModelImpl("Test");
        BlobHolder bh = adapters.getBlobHolderAdapter(doc);

        assertNotNull(bh);

        assertTrue(bh.getFilePath().startsWith("Test"));
        assertEquals("Test", bh.getBlob().getString());
    }

}
