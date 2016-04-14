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
 *     Kevin Leturc
 */
package org.nuxeo.ecm.core.storage.marklogic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.client.io.marker.StructureWriteHandle;
import com.marklogic.client.query.QueryManager;

/**
 * Query builder for a MarkLogic query.
 *
 * @since 8.2
 */
public class MarkLogicQueryBuilder {

    private static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;

    private static final Function<Object, JsonNode> VALUE_SERIALIZER = MarkLogicStateSerializer.SERIALIZER.getValueNodeSerializer();

    private static final String QUERY = "$query";

    private static final String NOT = "$not";

    private static final String OR = "$or";

    private final ObjectNode root;

    /** Cursor to "$query" object in MarkLogic query. */
    private final ObjectNode query;

    public MarkLogicQueryBuilder() {
        root = NODE_FACTORY.objectNode();
        query = NODE_FACTORY.objectNode();
        root.set(QUERY, query);
    }

    /**
     * Add an equal condition on {@code key}.
     *
     * @return the builder for convenient chaining
     */
    public MarkLogicQueryBuilder eq(String key, Object value) {
        query.set(MarkLogicHelper.KEY_SERIALIZER.apply(key), VALUE_SERIALIZER.apply(value));
        return this;
    }

    public MarkLogicQueryBuilder notIn(String key, Collection<?> values) {
        if (!values.isEmpty()) {
            ArrayNode andArray = values.stream()
                                       .map(VALUE_SERIALIZER)
                                       .map(value -> NODE_FACTORY.objectNode().set(
                                               MarkLogicHelper.KEY_SERIALIZER.apply(key), value))
                                       .reduce(NODE_FACTORY.arrayNode(), ArrayNode::add, ArrayNode::addAll);
            JsonNode andObject = NODE_FACTORY.objectNode().set(OR, andArray);
            query.set(NOT, andObject);
        }
        return this;
    }

    /**
     * This constraint does something only on {@link QueryManager}.
     */
    public MarkLogicQueryBuilder extract(String key1, String... key2) {
        if (key2 == null) {
            return extract(new String[] { key1 });
        }
        List<String> keys = new ArrayList<>(Arrays.asList(key2));
        keys.add(0, key1);
        return extract(keys.toArray(new String[keys.size()]));
    }

    /**
     * This constraint does something only on {@link QueryManager}.
     * "$snippet": { "$none": {} }
     */
    public MarkLogicQueryBuilder extract(String[] keys) {
        ObjectNode response = getOrInitNode(root, "$response");
        ObjectNode snippet = getOrInitNode(response, "$snippet");
        getOrInitNode(snippet, "$none");
        ObjectNode extract = getOrInitNode(response, "$extract");
        for (String key : keys) {
            extract.set(MarkLogicHelper.KEY_SERIALIZER.apply(key), NODE_FACTORY.objectNode());
        }
        return this;
    }

    private ObjectNode getOrInitNode(ObjectNode root, String key) {
        ObjectNode node = (ObjectNode) root.get(key);
        if (node == null)  {
            node = NODE_FACTORY.objectNode();
            root.set(key, node);
        }
        return node;
    }

    /**
     * Builds the MarkLogic query. Once the query is built, specifying additional constraint with the builder do not
     * alter the query built previously.
     *
     * @return the MarkLogic query
     */
    public StructureWriteHandle build() {
        return new JacksonHandle(root.deepCopy());
    }
}
