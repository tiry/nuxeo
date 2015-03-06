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

import java.io.File;
import java.io.IOException;
import javax.inject.Inject;

import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy({ "org.nuxeo.ecm.platform.commandline.executor", "org.nuxeo.ecm.platform.convert" })
public abstract class BaseConverterTest extends Assert {

    @Inject
    protected ConversionService cs;

    protected BlobHolder getBlobFromPath(String path, String srcMT) throws IOException {
        File file = FileUtils.getResourceFileFromContext(path);
        assertTrue(file.length() > 0);

        Blob blob = Blobs.createBlob(file);
        if (srcMT != null) {
            blob.setMimeType(srcMT);
        }
        blob.setFilename(file.getName());
        return new SimpleBlobHolder(blob);
    }

    protected BlobHolder getBlobFromPath(String path) throws IOException {
        return getBlobFromPath(path, null);
    }

    public static String readPdfText(File pdfFile) throws IOException {
        PDFTextStripper textStripper = new PDFTextStripper();
        PDDocument document = PDDocument.load(pdfFile);
        String text = textStripper.getText(document);
        document.close();
        return text.trim();
    }

    public static boolean isPDFA(File pdfFile) throws Exception {
        PDDocument pddoc = PDDocument.load(pdfFile);
        XMPMetadata xmp = pddoc.getDocumentCatalog().getMetadata().exportXMPMetadata();
        Document doc = xmp.getXMPDocument();
        // <rdf:Description xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/"
        // rdf:about="">
        // <pdfaid:part>1</pdfaid:part>
        // <pdfaid:conformance>A</pdfaid:conformance>
        // </rdf:Description>
        NodeList list = doc.getElementsByTagName("pdfaid:conformance");
        return list != null && "A".equals(list.item(0).getTextContent());
    }

}
