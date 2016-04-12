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

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.nuxeo.ecm.core.api.model.Delta;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.State.ListDiff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * MarkLogic Serializer to convert {@link State} into {@link String}.
 *
 * @since 8.2
 */
class MarkLogicStateSerializer implements Function<State, String> {

    public static final MarkLogicStateSerializer SERIALIZER = new MarkLogicStateSerializer();

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private final Function<State, ObjectNode> stateSerializer = new StateSerializer();

    private final Function<Object, JsonNode> valueSerializer = new ValueSerializer();

    private final Function<List<Object>, ArrayNode> listSerializer = new ListSerializer();

    private final Function<ListDiff, ObjectNode> listDiffSerializer = new ListDiffSerializer();

    @Override
    public String apply(State state) {
        return stateSerializer.apply(state).toString();
    }

    public Function<Object, JsonNode> getValueNodeSerializer() {
        return valueSerializer;
    }

    private class StateSerializer implements Function<State, ObjectNode> {

        @Override
        public ObjectNode apply(State state) {
            ObjectNode object = FACTORY.objectNode();
            for (Entry<String, Serializable> entry : state.entrySet()) {
                String key = MarkLogicHelper.KEY_SERIALIZER.apply(entry.getKey());
                JsonNode value = valueSerializer.apply(entry.getValue());
                object.set(key, value);
            }
            return object;
        }

    }

    private class ValueSerializer implements Function<Object, JsonNode> {

        @Override
        public JsonNode apply(Object value) {
            JsonNode result;
            if (value instanceof State) {
                result = stateSerializer.apply((State) value);
            } else if (value instanceof ListDiff) {
                result = listDiffSerializer.apply((ListDiff) value);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> values = (List<Object>) value;
                result = listSerializer.apply(values);
            } else if (value instanceof Object[]) {
                result = listSerializer.apply(Arrays.asList((Object[]) value));
            } else {
                if (value instanceof Calendar) {
                    DateTime dateTime = DateTime.now().withMillis(((Calendar) value).getTimeInMillis());
                    result = FACTORY.textNode(dateTime.toString(DATE_TIME_FORMATTER));
                } else if (value instanceof DateTime) {
                    result = FACTORY.textNode(((DateTime) value).toString(DATE_TIME_FORMATTER));
                } else if (value instanceof String) {
                    result = FACTORY.textNode((String) value);
                } else if (value instanceof Boolean) {
                    result = FACTORY.booleanNode((boolean) value);
                } else if (value instanceof Byte) {
                    result = FACTORY.numberNode((byte) value);
                } else if (value instanceof Short) {
                    result = FACTORY.numberNode((short) value);
                } else if (value instanceof Integer) {
                    result = FACTORY.numberNode((int) value);
                } else if (value instanceof Long) {
                    result = FACTORY.numberNode((long) value);
                } else if (value instanceof Float) {
                    result = FACTORY.numberNode((float) value);
                } else if (value instanceof Double) {
                    result = FACTORY.numberNode((double) value);
                } else if (value instanceof BigInteger) {
                    result = FACTORY.numberNode((BigInteger) value);
                } else if (value instanceof BigDecimal) {
                    result = FACTORY.numberNode((BigDecimal) value);
                } else if (value instanceof Delta) {
                    // TODO better handling for delta
                    result = FACTORY.numberNode(((Delta) value).longValue());
                } else {
                    // Valid case cause State don't have null values, except StateDiff do and we want null node to
                    // server-side javascript for update
                    result = FACTORY.nullNode();
                }
            }
            return result;
        }

    }

    private class ListSerializer implements Function<List<Object>, ArrayNode> {

        @Override
        public ArrayNode apply(List<Object> list) {
            return list.stream().map(valueSerializer).collect(FACTORY::arrayNode, ArrayNode::add, ArrayNode::addAll);
        }

    }

    private class ListDiffSerializer implements Function<ListDiff, ObjectNode> {

        @Override
        public ObjectNode apply(ListDiff listDiff) {
            ObjectNode diff = FACTORY.objectNode();
            diff.set("diff", listSerializer.apply(Optional.ofNullable(listDiff.diff).orElseGet(ArrayList::new)));
            diff.set("rpush", listSerializer.apply(Optional.ofNullable(listDiff.rpush).orElseGet(ArrayList::new)));
            return diff;
        }

    }

}
