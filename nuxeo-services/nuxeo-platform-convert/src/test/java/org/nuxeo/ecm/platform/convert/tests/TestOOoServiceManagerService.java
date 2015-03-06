/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 */

package org.nuxeo.ecm.platform.convert.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.FileInputStream;

import javax.inject.Inject;

import org.artofsolving.jodconverter.OfficeDocumentConverter;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.convert.ooomanager.OOoManagerComponent;
import org.nuxeo.ecm.platform.convert.ooomanager.OOoManagerDescriptor;
import org.nuxeo.ecm.platform.convert.ooomanager.OOoManagerService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.RuntimeHarness;

@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@LocalDeploy("org.nuxeo.ecm.platform.convert:test-ooo-manager-contrib.xml")
@Ignore
public class TestOOoServiceManagerService extends BaseConverterTest {

    @Inject
    RuntimeHarness tc;

    @Inject
    OOoManagerService ods;

    @Test
    public void testServiceRegistration() throws Exception {
        OfficeDocumentConverter converter = ods.getDocumentConverter();
        assertNotNull(converter);

        OOoManagerComponent odc = (OOoManagerComponent) ods;
        OOoManagerDescriptor desc = odc.getDescriptor();
        String[] pipes = desc.getPipeNames();
        assertEquals("pipe1", pipes[0]);
        assertEquals("pipe2", pipes[1]);
        assertEquals("pipe3", pipes[2]);

        int[] ports = desc.getPortNumbers();
        assertEquals(2003, ports[0]);
        assertEquals(2004, ports[1]);
        assertEquals(2005, ports[2]);
    }

    @Test
    public void testSocketConnection() throws Exception {
        ods.stopOOoManager();
        Framework.getProperties().load(new FileInputStream(tc.getResource("jodSocket.properties").getFile()));
        ods.startOOoManager();
        OfficeDocumentConverter converter = ods.getDocumentConverter();
        assertNotNull(converter);
    }

    @Test
    @Ignore
    public void testPipeConnection() throws Exception {
        ods.stopOOoManager();
        Framework.getProperties().load(new FileInputStream(tc.getResource("jodPipe.properties").getFile()));
        ods.startOOoManager();
        ods = Framework.getLocalService(OOoManagerService.class);
        assertNotNull(ods);
        OfficeDocumentConverter converter = ods.getDocumentConverter();
        assertNotNull(converter);
    }

}
