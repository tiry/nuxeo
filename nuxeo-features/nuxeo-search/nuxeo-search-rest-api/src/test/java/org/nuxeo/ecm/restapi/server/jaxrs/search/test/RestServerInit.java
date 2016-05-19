/*
 * (C) Copyright 2013-2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     dmetzler
 *     Gabriel Barata <gbarata@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.server.jaxrs.search.test;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import javax.ws.rs.core.MultivaluedMap;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.test.annotations.RepositoryInit;
import org.nuxeo.ecm.platform.search.core.SavedSearch;
import org.nuxeo.ecm.platform.search.core.SavedSearchService;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.ecm.webengine.JsonFactoryManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * @since 8.3
 */
public class RestServerInit implements RepositoryInit {

    @Override
    public void populate(CoreSession session) {
        JsonFactoryManager jsonFactoryManager = Framework.getLocalService(JsonFactoryManager.class);
        if (!jsonFactoryManager.isStackDisplay()) {
            jsonFactoryManager.toggleStackDisplay();
        }
        // try to prevent NXP-15404
        // clearRepositoryCaches(session.getRepositoryName());
        // Create some docs
        for (int i = 0; i < 5; i++) {
            DocumentModel doc = session.createDocumentModel("/", "folder_" + i, "Folder");
            doc.setPropertyValue("dc:title", "Folder " + i);
            if (i == 0) {
                // set dc:issued value for queries on dates
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.YEAR, 2007);
                cal.set(Calendar.MONTH, 1); // 0-based
                cal.set(Calendar.DAY_OF_MONTH, 17);
                doc.setPropertyValue("dc:issued", cal);
            }
            doc = session.createDocument(doc);
        }

        for (int i = 0; i < 5; i++) {
            DocumentModel doc = session.createDocumentModel("/folder_1", "note_" + i, "Note");
            doc.setPropertyValue("dc:title", "Note " + i);
            doc.setPropertyValue("dc:source", "Source" + i);
            doc.setPropertyValue("dc:nature", "Nature" + i % 2);
            doc.setPropertyValue("dc:coverage", "Coverage" + i % 3);
            doc.setPropertyValue("note:note", "Note " + i);
            doc = session.createDocument(doc);
        }

        // Create a file
        DocumentModel doc = session.createDocumentModel("/folder_2", "file", "File");
        doc.setPropertyValue("dc:title", "File");
        doc = session.createDocument(doc);
        // upload file blob
        File fieldAsJsonFile = FileUtils.getResourceFileFromContext("blob.json");
        try {
            Blob fb = Blobs.createBlob(fieldAsJsonFile, "image/jpeg");
            DocumentHelper.addBlob(doc.getProperty("file:content"), fb);
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
        session.saveDocument(doc);

        // Create some saved searches
        SavedSearchService savedSearchService = Framework.getLocalService(SavedSearchService.class);

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();

        params.add("pageSize", "2");
        params.add("queryParams", "$currentUser");
        params.add("query", "select * from Document where " + "dc:creator = ?");
        savedSearchService.saveSearch(session, "my saved search 1", SavedSearch.SavedSearchType.QUERY, "NXQL",
            params).getId();

        params = new MultivaluedMapImpl();
        params.add("queryParams", RestServerInit.getFolder(1, session).getId());
        savedSearchService.saveSearch(session, "my saved search 2", SavedSearch.SavedSearchType.PAGE_PROVIDER,
            "TEST_PP", params).getId();

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

    public static DocumentModel getFolder(int index, CoreSession session) {
        return session.getDocument(new PathRef("/folder_" + index));
    }

    public static String getSavedSearchId(int index, CoreSession session) {
        UserWorkspaceService userWorkspaceService = Framework.getLocalService(UserWorkspaceService.class);
        DocumentModel uws = userWorkspaceService.getCurrentUserPersonalWorkspace(session, null);
        return session.getDocument(new PathRef(uws.getPathAsString() + "/my saved search " + index)).getId();
    }

}
