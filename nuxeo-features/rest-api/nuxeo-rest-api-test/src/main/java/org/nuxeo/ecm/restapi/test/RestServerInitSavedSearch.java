/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Gabriel Barata <gbarata@nuxeo.com
 */
package org.nuxeo.ecm.restapi.test;

import javax.ws.rs.core.MultivaluedMap;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.test.annotations.RepositoryInit;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.ecm.restapi.jaxrs.io.search.SavedSearch;
import org.nuxeo.ecm.restapi.jaxrs.io.search.SavedSearchService;
import org.nuxeo.runtime.api.Framework;

import com.sun.jersey.core.util.MultivaluedMapImpl;

import java.util.Calendar;

/**
 * @since 8.3
 */
public class RestServerInitSavedSearch implements RepositoryInit {


    RestServerInit init = new RestServerInit();

    @Override
    public void populate(CoreSession session) {
        init.populate(session);

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
    }

    public static String getSavedSearchId(int index, CoreSession session) {
        UserWorkspaceService userWorkspaceService = Framework.getLocalService(UserWorkspaceService.class);
        DocumentModel uws = userWorkspaceService.getCurrentUserPersonalWorkspace(session, null);
        return session.getDocument(new PathRef(uws.getPathAsString() + "/my saved search " + index)).getId();
    }

    public static DocumentModel getFolder(int index, CoreSession session) {
        return session.getDocument(new PathRef("/folder_" + index));
    }

}
