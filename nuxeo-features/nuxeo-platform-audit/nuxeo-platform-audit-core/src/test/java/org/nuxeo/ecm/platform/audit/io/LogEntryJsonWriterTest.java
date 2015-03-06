/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */

package org.nuxeo.ecm.platform.audit.io;

import static org.junit.Assert.assertNotNull;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriterTest;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.platform.audit.AuditFeature;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.AuditReader;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.impl.LogEntryImpl;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.LocalDeploy;

@Features(AuditFeature.class)
@LocalDeploy("org.nuxeo.ecm.platform.audit:test-pageprovider-contrib.xml")
public class LogEntryJsonWriterTest extends AbstractJsonWriterTest.External<LogEntryJsonWriter, LogEntry> {

    public LogEntryJsonWriterTest() {
        super(LogEntryJsonWriter.class, LogEntry.class);
    }

    @Inject
    private PageProviderService pps;

    @Inject
    private CoreSession session;

    @Inject
    AuditFeature audit;

    @Before
    public void createTestEntries() {

        AuditReader reader = Framework.getLocalService(AuditReader.class);
        assertNotNull(reader);

        AuditLogger logger = Framework.getLocalService(AuditLogger.class);
        assertNotNull(logger);
        LogEntry entry = new LogEntryImpl();
        entry.setRepositoryId("test");
        entry.setCategory("category");
        entry.setEventId("event");
        entry.setPrincipalName("Administrator");
        entry.setDocPath("/");
        entry.setEventDate(Calendar.getInstance().getTime());
        entry.setDocType("docType");
        entry.setDocUUID("uuid");

        logger.addLogEntries(Collections.singletonList(entry));

    }

    @Test
    public void test() throws Exception {
        PageProvider<?> pp = pps.getPageProvider("DOCUMENT_HISTORY_PROVIDER", null, Long.valueOf(20), Long.valueOf(0),
                new HashMap<String, Serializable>(), "uuid");
        @SuppressWarnings("unchecked")
        List<LogEntry> entries = (List<LogEntry>) pp.getCurrentPage();
        JsonAssert json = jsonAssert(entries.get(0));
        json.properties(14);
        json.has("entity-type").isEquals("logEntry");
        json.has("id").isInt();
        json.has("category").isEquals("category");
        json.has("principalName").isEquals("Administrator");
        json.has("docPath").isEquals("/");
        json.has("docType").isEquals("docType");
        json.has("docUUID").isEquals("uuid");
        json.has("eventId").isText();
        json.has("repositoryId").isEquals("test");
        json.has("eventDate").isText();
        json.has("logDate").isText();
        try {
            json.has("comment").isText();
        } catch (AssertionError e) {
            json.has("comment").isNull();
        }
        try {
            json.has("docLifeCycle").isText();
        } catch (AssertionError e) {
            json.has("docLifeCycle").isNull();
        }
        json.has("extended").properties(0);
    }

}
