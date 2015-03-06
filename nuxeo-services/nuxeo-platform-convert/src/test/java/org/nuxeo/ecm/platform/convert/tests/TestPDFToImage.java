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

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Assume;
import org.junit.Test;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.convert.api.ConverterCheckResult;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandAvailability;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandLineExecutorService;
import org.nuxeo.runtime.api.Framework;

public class TestPDFToImage extends BaseConverterTest {

    private static final Log log = LogFactory.getLog(TestPDFToImage.class);

    @Test
    public void testConverter() throws Exception {

        String converterName = cs.getConverterName("application/pdf", "image/jpeg");
        assertEquals("pdf2image", converterName);

        CommandLineExecutorService cles = Framework.getLocalService(CommandLineExecutorService.class);
        assertNotNull(cles);

        ConverterCheckResult check = cs.isConverterAvailable(converterName);
        assertNotNull(check);
        Assume.assumeTrue(
                String.format("Skipping PDF2Image tests since commandLine is not installed:\n"
                        + "- installation message: %s\n- error message: %s", check.getInstallationMessage(),
                        check.getErrorMessage()), check.isAvailable());

        CommandAvailability ca = cles.getCommandAvailability("pdftoimage");
        Assume.assumeTrue("convert command is not available, skipping test", ca.isAvailable());

        BlobHolder pdfBH = getBlobFromPath("test-docs/hello.pdf");
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put("targetFilePath", "hello.png");

        BlobHolder result = cs.convert(converterName, pdfBH, parameters);
        assertNotNull(result);

        List<Blob> blobs = result.getBlobs();
        assertNotNull(blobs);
        assertEquals(1, blobs.size());

        Blob mainBlob = result.getBlob();
        assertEquals("hello.png", mainBlob.getFilename());
    }

}
