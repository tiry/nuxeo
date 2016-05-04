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
package org.nuxeo.ecm.restapi.server.jaxrs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.nuxeo.ecm.automation.server.jaxrs.RestOperationException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.restapi.jaxrs.io.search.InvalidSearchParameterException;
import org.nuxeo.ecm.restapi.jaxrs.io.search.SavedSearch;
import org.nuxeo.ecm.restapi.jaxrs.io.search.SavedSearchImpl;
import org.nuxeo.ecm.restapi.jaxrs.io.search.SavedSearchService;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 8.3 Search endpoint to perform queries via rest api, with support to save and execute saved search queries.
 */
@WebObject(type = "search")
public class SearchObject extends QueryExecutor {

    private static final String APPLICATION_JSON_NXENTITY = "application/json+nxentity";

    public static final String SAVED_SEARCHES_PAGE_PROVIDER = "GET_SAVED_SEARCHES";

    public static final String SAVED_SEARCHES_PAGE_PROVIDER_PARAMS = "GET_SAVED_SEARCHES_FOR_PAGE_PROVIDER";

    public static final String PAGE_PROVIDER_NAME_PARAM = "pageProvider";

    protected SavedSearchService savedSearchService;

    @Override
    public void initialize(Object... args) {
        initExecutor();
        savedSearchService = Framework.getLocalService(SavedSearchService.class);
    }

    @GET
    @Path("lang/{queryLanguage}/execute")
    public Object doQueryByLang(@Context UriInfo uriInfo, @PathParam("queryLanguage") String queryLanguage)
            throws RestOperationException {
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        return queryByLang(queryLanguage, queryParams);
    }

    @GET
    @Path("pp/{pageProviderName}/execute")
    public Object doQueryByPageProvider(@Context UriInfo uriInfo, @PathParam("pageProviderName") String pageProviderName)
            throws RestOperationException {
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        return queryByPageProvider(pageProviderName, queryParams);
    }

    @GET
    @Path("pp/{pageProviderName}")
    public Object doGetPageProviderDefinition(@PathParam("pageProviderName") String pageProviderName)
            throws RestOperationException, IOException {
        return getPageProviderDefinition(pageProviderName);
    }

    @GET
    @Path("saved")
    public List<SavedSearch> doGetSavedSearches(@Context UriInfo uriInfo) throws RestOperationException {
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        DocumentModelList results = queryParams.containsKey(PAGE_PROVIDER_NAME_PARAM) ? queryByPageProvider(
                SAVED_SEARCHES_PAGE_PROVIDER_PARAMS, queryParams) : queryByPageProvider(SAVED_SEARCHES_PAGE_PROVIDER,
                queryParams);
        List<SavedSearch> savedSearches = new ArrayList<SavedSearch>(results.size());
        for (DocumentModel doc : results) {
            savedSearches.add(doc.getAdapter(SavedSearch.class));
        }
        return savedSearches;
    }

    @POST
    @Path("saved")
    @Consumes({ APPLICATION_JSON_NXENTITY, "application/json" })
    public Response doSaveSearch(SavedSearch search) throws RestOperationException {
        try {
            return Response.ok(savedSearchService.saveSearch(ctx.getCoreSession(), search)).build();
        } catch (InvalidSearchParameterException e) {
            RestOperationException err = new RestOperationException(e.getMessage());
            err.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            throw err;
        }
    }

    @GET
    @Path("saved/{id}")
    public Response doGetSavedSearch(@PathParam("id") String id) throws RestOperationException {
        SavedSearch search;
        try {
            search = savedSearchService.getSearch(ctx.getCoreSession(), id);
        } catch (DocumentNotFoundException e) {
            RestOperationException err = new RestOperationException("unknown id: " + e.getMessage());
            err.setStatus(HttpServletResponse.SC_NOT_FOUND);
            throw err;
        }
        if (search == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(search).build();
    }

    @PUT
    @Path("saved/{id}")
    @Consumes({ APPLICATION_JSON_NXENTITY, "application/json" })
    public Response doUpdateSavedSearch(SavedSearch search, @PathParam("id") String id) throws RestOperationException {
        SavedSearch savedSearch;
        try {
            savedSearch = savedSearchService.updateSearch(ctx.getCoreSession(), id, search);
        } catch (InvalidSearchParameterException e) {
            RestOperationException err = new RestOperationException(e.getMessage());
            err.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            throw err;
        } catch (DocumentNotFoundException e) {
            RestOperationException err = new RestOperationException("unknown id: " + e.getMessage());
            err.setStatus(HttpServletResponse.SC_NOT_FOUND);
            throw err;
        }
        if (savedSearch == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(savedSearch).build();
    }

    @DELETE
    @Path("saved/{id}")
    public Response doDeleteSavedSearch(@PathParam("id") String id) throws RestOperationException {
        try {
            savedSearchService.deleteSearch(ctx.getCoreSession(), id);
        } catch (DocumentNotFoundException e) {
            RestOperationException err = new RestOperationException(e.getMessage());
            err.setStatus(HttpServletResponse.SC_NOT_FOUND);
            throw err;
        }
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @GET
    @Path("saved/{id}/execute")
    public Object doExecuteSavedSearch(@PathParam("id") String id) throws RestOperationException {
        SavedSearch search = savedSearchService.getSearch(ctx.getCoreSession(), id);
        if (search == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (SavedSearchImpl.SavedSearchType.PAGE_PROVIDER.equalsName(search.getSearchType().toString())) {
            return queryByPageProvider(search.getLangOrProviderName(), search.getParams());
        } else {
            return queryByLang(search.getLangOrProviderName(), search.getParams());
        }
    }
}
