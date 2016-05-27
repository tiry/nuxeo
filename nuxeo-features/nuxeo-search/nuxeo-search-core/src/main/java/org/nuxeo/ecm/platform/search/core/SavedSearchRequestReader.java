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

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.codehaus.jackson.JsonNode;
import org.nuxeo.ecm.core.io.marshallers.json.EntityJsonReader;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.webengine.WebException;

/**
 * @since 8.3
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class SavedSearchRequestReader extends EntityJsonReader<SavedSearch> {

    public SavedSearchRequestReader() {
        super(SavedSearchWriter.ENTITY_TYPE);
    }

    @Override
    protected SavedSearch readEntity(JsonNode jn) throws IOException {
        String id = getStringField(jn, "id");
        String title = getStringField(jn, "title");
        String searchType = getStringField(jn, "searchType");
        String langOrProviderName = getStringField(jn, "langOrProviderName");
        JsonNode queryParamsNode = jn.has("params") ? jn.get("params") : null;

        Map<String, String> queryParams = new HashMap<>();

        if (queryParamsNode != null) {
            Iterator<Map.Entry<String, JsonNode>> fields = queryParamsNode.getFields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> fieldEntry = fields.next();
                if (fieldEntry.getValue().isValueNode()) {
                    queryParams.put(fieldEntry.getKey(), fieldEntry.getValue().getTextValue());
                } else {
                    throw new WebException("Invalid query parameters format" + fieldEntry.getKey(),
                            Response.Status.BAD_REQUEST.getStatusCode());
                }
            }
        }

        SavedSearch.SavedSearchType type = null;
        if (SavedSearch.SavedSearchType.QUERY.equalsName(searchType)) {
            type = SavedSearch.SavedSearchType.QUERY;
        } else if (SavedSearch.SavedSearchType.PAGE_PROVIDER.equalsName(searchType)) {
            type = SavedSearch.SavedSearchType.PAGE_PROVIDER;
        }

        return new SavedSearchRequest(id, title, type, langOrProviderName, queryParams);
    }
}
