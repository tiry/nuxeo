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
 *     Gabriel Barata <gbarata@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.jaxrs.io.search;

import java.security.Principal;

import javax.ws.rs.core.MultivaluedMap;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.runtime.api.Framework;

import com.google.common.base.Strings;

/**
 * @since 8.3
 */
public class SavedSearchServiceImpl implements SavedSearchService {

    @Override
    public SavedSearch saveSearch(CoreSession session, SavedSearch search) throws InvalidSearchParameterException {
        return saveSearch(session, search.getTitle(), search.getSearchType(), search.getLangOrProviderName(),
            search.getParams());
    }

    @Override
    public SavedSearch saveSearch(CoreSession session, String title, SavedSearch.SavedSearchType searchType,
        String langOrProviderName, MultivaluedMap<String, String> params) throws InvalidSearchParameterException {
        if (Strings.isNullOrEmpty(title)) {
            throw new InvalidSearchParameterException("invalid title");
        }
        if (Strings.isNullOrEmpty(langOrProviderName)) {
            throw new InvalidSearchParameterException("invalid language or provider name");
        }

        UserWorkspaceService userWorkspaceService = Framework.getLocalService(UserWorkspaceService.class);
        DocumentModel uws = userWorkspaceService.getCurrentUserPersonalWorkspace(session, null);

        DocumentModel savedSearchDoc = session.createDocumentModel(uws.getPathAsString(), title,
            SavedSearchConstants.SAVED_SEARCH_TYPE_NAME);

        SavedSearch savedSearch = savedSearchDoc.getAdapter(SavedSearch.class);
        savedSearch.setTitle(title);
        savedSearch.setLangOrProviderName(langOrProviderName);
        savedSearch.setSearchType(searchType);
        savedSearch.setParams(params);

        savedSearchDoc = session.createDocument(savedSearchDoc);
        savedSearch = savedSearchDoc.getAdapter(SavedSearch.class);

        ACP acp = savedSearchDoc.getACP();
        ACL acl = acp.getOrCreateACL(ACL.LOCAL_ACL);
        Principal principal = session.getPrincipal();
        if (principal != null) {
            acl.add(new ACE(principal.getName(), SecurityConstants.EVERYTHING, true));
        }

        acp.addACL(acl);
        savedSearchDoc.setACP(acp, true);
        session.saveDocument(savedSearchDoc);

        return savedSearch;
    }

    @Override
    public SavedSearch getSearch(CoreSession session, String id) {
        DocumentRef docRef = new IdRef(id);
        DocumentModel savedSearchDoc = session.getDocument(docRef);
        if (savedSearchDoc != null) {
            SavedSearch search = savedSearchDoc.getAdapter(SavedSearch.class);
            if (search != null) {
                return search;
            }
        }
        return null;
    }

    @Override
    public SavedSearch updateSearch(CoreSession session, String id, SavedSearch search) throws InvalidSearchParameterException {
        if (Strings.isNullOrEmpty(search.getTitle())) {
            throw new InvalidSearchParameterException("title cannot be empty");
        }
        if (Strings.isNullOrEmpty(search.getLangOrProviderName())) {
            throw new InvalidSearchParameterException("language or provider name cannot be empty");
        }

        DocumentRef docRef = new IdRef(id);
        DocumentModel savedSearchDoc = session.getDocument(docRef);
        if (savedSearchDoc == null) {
            return null;
        }
        SavedSearch savedSearch = savedSearchDoc.getAdapter(SavedSearch.class);
        if (savedSearch == null) {
            return null;
        }

        savedSearch.setTitle(search.getTitle());
        savedSearch.setLangOrProviderName(search.getLangOrProviderName());
        savedSearch.setSearchType(search.getSearchType());
        savedSearch.setParams(search.getParams());

        savedSearchDoc = session.saveDocument(savedSearchDoc);
        savedSearch = savedSearchDoc.getAdapter(SavedSearch.class);

        return savedSearch;
    }

    @Override
    public void deleteSearch(CoreSession session, String id) {
        DocumentRef docRef = new IdRef(id);
        session.removeDocument(docRef);
    }
}
