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
package org.nuxeo.ecm.platform.search.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * @since 8.3
 */
public class ParameterizedSavedSearch implements SavedSearch {

    private DocumentModel doc;

    public ParameterizedSavedSearch(DocumentModel doc) {
        this.doc = doc;
    }

    @Override
    public String getId() {
        return doc.getId();
    }

    @Override
    public String getTitle() {
        return getPropertyValue(SavedSearchConstants.TITLE_PROPERTY_NAME);
    }

    @Override
    public Map<String, String> getParams() {
        List<Map<String, String>> paramsProperty = getPropertyValue(SavedSearchConstants.PARAMS_PROPERTY_NAME);
        Map<String, String> params = new HashMap<>();
        for (Map<String, String> map : paramsProperty) {
            params.put(map.get("key"), map.get("value"));
        }

        return params;
    }

    @Override
    public SavedSearchType getSearchType() {
        String typeStr = getPropertyValue(SavedSearchConstants.SEARCH_TYPE_PROPERTY_NAME);
        ;
        if (SavedSearchType.PAGE_PROVIDER.equalsName(typeStr)) {
            return SavedSearchType.PAGE_PROVIDER;
        } else {
            return SavedSearchType.QUERY;
        }
    }

    @Override
    public String getLangOrProviderName() {
        return getPropertyValue(SavedSearchConstants.LANG_OR_PROVIDER_NAME_PROPERTY_NAME);
    }

    @Override
    public DocumentModel getDocument() {
        return doc;
    }

    @Override
    public void setTitle(String title) {
        doc.setPropertyValue(SavedSearchConstants.TITLE_PROPERTY_NAME, title);
    }

    @Override
    public void setParams(Map<String, String> params) {
        // TODO clean up previous values on every setParams
        List<Map<String, String>> paramsProperty = getPropertyValue(SavedSearchConstants.PARAMS_PROPERTY_NAME);
        if (paramsProperty == null) {
            paramsProperty = new ArrayList<>();
        }

        Map<String, String> variable;
        for (String key : params.keySet()) {
            String value = params.get(key);
            variable = new HashMap<>(1);
            variable.put("key", key);
            variable.put("value", value);
            paramsProperty.add(variable);
        }
        doc.setPropertyValue(SavedSearchConstants.PARAMS_PROPERTY_NAME, (Serializable) paramsProperty);
    }

    @Override
    public void setSearchType(SavedSearchType type) {
        doc.setPropertyValue(SavedSearchConstants.SEARCH_TYPE_PROPERTY_NAME, type.toString());
    }

    @Override
    public void setLangOrProviderName(String langOrProviderName) {
        doc.setPropertyValue(SavedSearchConstants.LANG_OR_PROVIDER_NAME_PROPERTY_NAME, langOrProviderName);
    }

    @SuppressWarnings("unchecked")
    protected <T> T getPropertyValue(String propertyName) {
        Serializable value = doc.getPropertyValue(propertyName);
        if (value instanceof Object[]) {
            value = new ArrayList<Object>(Arrays.asList((Object[]) value));
        }
        return (T) value;
    }
}
