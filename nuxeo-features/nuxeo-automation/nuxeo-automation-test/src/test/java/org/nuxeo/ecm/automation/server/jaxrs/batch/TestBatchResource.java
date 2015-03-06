/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.ecm.automation.server.jaxrs.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.model.Blob;
import org.nuxeo.ecm.automation.core.operations.blob.GetDocumentBlob;
import org.nuxeo.ecm.automation.test.EmbeddedAutomationServerFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.JettyFeature;
import org.nuxeo.runtime.test.runner.RuntimeHarness;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.nuxeo.transientstore.test.TransientStoreFeature;

/**
 * Tests file upload with the {@link BatchResource}.
 * <p>
 * Uses {@link URLConnection}.
 *
 * @author Antoine Taillefer
 * @deprecated since 7.4
 * @see {@link BatchUploadTest}
 */
@Deprecated
@RunWith(FeaturesRunner.class)
@Features({ TransientStoreFeature.class, EmbeddedAutomationServerFeature.class })
public class TestBatchResource {

    @Inject
    protected RuntimeHarness harness;

    @Inject
    protected CoreSession session;

    @Inject
    protected Session clientSession;

    @Inject
    JettyFeature jetty;

    @Test(expected = NuxeoException.class)
    public void testBatchUploadClientGeneratedIdNotAllowed() throws IOException {
        batchUpload(UUID.randomUUID().toString());
    }

    @Test
    public void testBatchUploadClientGeneratedIdAllowed() throws Exception {
        harness.deployContrib("org.nuxeo.ecm.automation.test.test",
                "test-batchmanager-client-generated-id-allowed-contrib.xml");
        String batchId = UUID.randomUUID().toString();
        String responseBatchId = batchUpload(batchId);
        assertEquals(batchId, responseBatchId);
    }

    @Test
    public void testBatchUploadServerGeneratedId() throws IOException {
        String batchId = Framework.getService(BatchManager.class).initBatch();
        assertEquals(batchId, batchUpload(batchId));
    }

    @Test
    public void testBatchUpload() throws Exception {

        // Create a File document
        DocumentModel file = session.createDocumentModel("/", "testFile", "File");
        file = session.createDocument(file);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        String batchId = Framework.getService(BatchManager.class).initBatch();
        batchUpload(batchId);
        batchExecute(batchId, file);

        // Get blob from document and check its content
        Blob blob = (Blob) clientSession.newRequest(GetDocumentBlob.ID).setInput(file.getPathAsString()).execute();
        assertNotNull(blob);
        String blobString = new String(IOUtils.toByteArray(blob.getStream()));
        assertEquals("This is the content of a new file.", blobString);
    }

    String batchUpload(String id) throws IOException {
        String uploadURL = jetty.getConnectionURL("/automation/batch/upload");
        String fileIndex = "0";
        String fileName = "New file.txt";
        String mimeType = "text/plain";
        String content = "This is the content of a new file.";
        return batchUpload(uploadURL, id, fileIndex, fileName, mimeType, content);
    }

    protected String batchUpload(String urlStr, String batchId, String fileIndex, String fileName, String mimeType,
            String content) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            // Set request headers
            byte[] bytes = content.getBytes();
            String fileSize = Integer.toString(bytes.length);
            conn.setRequestProperty("Authorization", getAuthHeader("Administrator", "Administrator"));
            if (batchId != null) {
                conn.setRequestProperty("X-Batch-Id", batchId);
            }
            conn.setRequestProperty("X-File-Idx", fileIndex);
            conn.setRequestProperty("X-File-Name", fileName);
            conn.setRequestProperty("X-File-Size", fileSize);
            conn.setRequestProperty("X-File-Type", mimeType);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Length", fileSize);
            // Write bytes
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                IOUtils.write(content, os);
            }
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new NuxeoException("Batch upload request failed with status code " + conn.getResponseCode());
            }
            // Read response and return batch id
            try (InputStream is = conn.getInputStream()) {
                JsonNode node = new ObjectMapper().readTree(is);
                return node.get("batchId").getValueAsText();
            }
        } finally {
            conn.disconnect();
        }
    }

    boolean batchExecute(String id, DocumentModel file) throws IOException {
        String executeURL = jetty.getConnectionURL("/automation/batch/execute");
        String fileIndex = "0";
        return batchExecuteAttachBlob(executeURL, id, fileIndex, file.getPathAsString());
    }

    protected boolean batchExecuteAttachBlob(String urlStr, String batchId, String fileIndex, String docPath)
            throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            // Set request headers
            conn.setRequestProperty("Authorization", getAuthHeader("Administrator", "Administrator"));
            conn.setRequestProperty("Content-Type", "application/json+nxrequest");
            conn.setRequestProperty("Accept", "application/json+nxentity, */*");
            conn.setRequestProperty("X-NXDocumentProperties", "*");
            // Write JSON data
            conn.setDoOutput(true);
            String JSONData = String.format(
                    "{\"params\": {\"operationId\": \"%s\", \"batchId\": \"%s\", \"fileIdx\": \"%s\", \"document\": \"%s\"}}",
                    "Blob.Attach", batchId, fileIndex, docPath);
            try (OutputStream os = conn.getOutputStream()) {
                IOUtils.write(JSONData, os);
            }
            // Consume response and return true if OK
            try (InputStream is = conn.getInputStream()) {
                IOUtils.toByteArray(is);
                return conn.getResponseCode() == HttpURLConnection.HTTP_OK;
            }
        } finally {
            conn.disconnect();
        }
    }

    protected String getAuthHeader(String userName, String password) {
        return "Basic " + new String(Base64.encodeBase64((userName + ":" + password).getBytes()));
    }

}
