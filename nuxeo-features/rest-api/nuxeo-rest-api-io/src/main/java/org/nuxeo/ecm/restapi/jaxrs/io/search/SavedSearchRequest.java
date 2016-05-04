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

import javax.ws.rs.core.MultivaluedMap;

/**
 * @since 8.3
 */
public class SavedSearchRequest implements SavedSearch {

    protected String id;

    protected String title;

    protected SavedSearchType searchType;

    protected String langOrProviderName;

    protected MultivaluedMap<String,String> params;

    public SavedSearchRequest(String id, String title, SavedSearchType searchType, String langOrProviderName,
        MultivaluedMap<String,String> params) {
        this.id = id;
        this.title = title;
        this.searchType = searchType;
        this.langOrProviderName = langOrProviderName;
        this.params = params;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public MultivaluedMap<String,String> getParams() {
        return params;
    }

    @Override
    public SavedSearchType getSearchType() {
        return searchType;
    }

    @Override
    public String getLangOrProviderName() {
        return langOrProviderName;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public void setSearchType(SavedSearchType searchType) {
        this.searchType = searchType;
    }

    @Override
    public void setLangOrProviderName(String langOrProviderName) {
        this.langOrProviderName = langOrProviderName;
    }

    @Override
    public void setParams(MultivaluedMap<String,String> params) {
        this.params = params;
    }
}
