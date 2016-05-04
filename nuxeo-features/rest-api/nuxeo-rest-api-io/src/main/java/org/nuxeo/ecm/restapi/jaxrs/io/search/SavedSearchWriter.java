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

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;
import java.util.Iterator;

import org.codehaus.jackson.JsonGenerator;
import org.nuxeo.ecm.core.io.marshallers.json.ExtensibleEntityJsonWriter;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;

/**
 * @since 8.3
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class SavedSearchWriter extends ExtensibleEntityJsonWriter<SavedSearch> {

    public static final String ENTITY_TYPE = "savedSearch";

    public SavedSearchWriter() {
        super(ENTITY_TYPE, SavedSearch.class);
    }

    @Override
    protected void writeEntityBody(SavedSearch search, JsonGenerator jg) throws IOException {
        jg.writeStringField("id", search.getId());
        jg.writeStringField("title", search.getTitle());
        jg.writeStringField("searchType", search.getSearchType().toString());
        jg.writeStringField("langOrProviderName", search.getLangOrProviderName());

        if (search.getParams() != null) {
            jg.writeObjectFieldStart("params");

            Iterator<String> it = search.getParams().keySet().iterator();
            while (it.hasNext()) {
                String param = it.next();
                if (search.getParams().get(param).size() > 1) {
                    jg.writeArrayFieldStart(param);
                    for (String val : search.getParams().get(param)) {
                        jg.writeString(val);
                    }
                    jg.writeEndArray();
                } else {
                    jg.writeStringField(param, search.getParams().get(param).get(0));
                }
            }
            jg.writeEndObject();
        }
    }
}
