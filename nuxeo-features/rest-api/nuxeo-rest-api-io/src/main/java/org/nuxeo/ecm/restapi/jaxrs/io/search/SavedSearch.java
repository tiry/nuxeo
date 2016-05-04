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

import java.io.Serializable;

import javax.ws.rs.core.MultivaluedMap;

/**
 * @since 8.3
 */
public interface SavedSearch extends Serializable {

    enum SavedSearchType {

        QUERY("query"),

        PAGE_PROVIDER("pageProvider");

        private final String name;

        SavedSearchType(final String s) {
            name = s;
        }

        public boolean equalsName(String otherName) {
            return (otherName == null) ? false : name.equals(otherName);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    String getId();

    String getTitle();

    MultivaluedMap<String,String> getParams();

    SavedSearchType getSearchType();

    String getLangOrProviderName();

    void setTitle(String title);

    void setParams(MultivaluedMap<String,String> params);

    void setSearchType(SavedSearchType type);

    void setLangOrProviderName(String langOrProviderName);

}
