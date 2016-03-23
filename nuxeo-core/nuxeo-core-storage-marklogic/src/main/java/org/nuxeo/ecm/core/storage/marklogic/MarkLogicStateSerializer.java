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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;

import org.nuxeo.ecm.core.storage.State;

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

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private final Function<State, ObjectNode> stateSerializer;

    private final Function<Object, Optional<JsonNode>> valueSerializer;

    private final Function<List<Object>, ArrayNode> listSerializer;

    public MarkLogicStateSerializer() {
        this.stateSerializer = new StateSerializer();
        this.valueSerializer = new ValueSerializer();
        this.listSerializer = new ListSerializer();
    }

    @Override
    public String apply(State state) {
        return stateSerializer.apply(state).toString();
    }

    public Function<Serializable, Optional<String>> getValueSerializer() {
        return valueSerializer.andThen(node -> node.map(JsonNode::toString));
    }

    private class StateSerializer implements Function<State, ObjectNode> {

        @Override
        public ObjectNode apply(State state) {
            ObjectNode object = FACTORY.objectNode();
            for (Entry<String, Serializable> entry : state.entrySet()) {
                valueSerializer.apply(entry.getValue()).ifPresent(value -> object.set(entry.getKey(), value));
            }
            return object;
        }

    }

    private class ValueSerializer implements Function<Object, Optional<JsonNode>> {

        @Override
        public Optional<JsonNode> apply(Object value) {
            Optional<JsonNode> result;
            if (value instanceof State) {
                result = Optional.of(stateSerializer.apply((State) value));
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> values = (List<Object>) value;
                result = Optional.of(listSerializer.apply(values));
            } else if (value instanceof Object[]) {
                result = Optional.of(listSerializer.apply(Arrays.asList((Object[]) value)));
            } else {
                if (value instanceof Calendar) {
                    LocalDateTime dateTime = LocalDateTime.ofInstant(((Calendar) value).toInstant(),
                            ZoneId.systemDefault());
                    result = Optional.of(FACTORY.textNode(dateTime.toString()));
                } else if (value instanceof String) {
                    result = Optional.of(FACTORY.textNode((String) value));
                } else if (value instanceof Boolean) {
                    result = Optional.of(FACTORY.booleanNode((boolean) value));
                } else if (value instanceof Byte) {
                    result = Optional.of(FACTORY.numberNode((byte) value));
                } else if (value instanceof Short) {
                    result = Optional.of(FACTORY.numberNode((short) value));
                } else if (value instanceof Integer) {
                    result = Optional.of(FACTORY.numberNode((int) value));
                } else if (value instanceof Long) {
                    result = Optional.of(FACTORY.numberNode((long) value));
                } else if (value instanceof Float) {
                    result = Optional.of(FACTORY.numberNode((float) value));
                } else if (value instanceof Double) {
                    result = Optional.of(FACTORY.numberNode((double) value));
                } else if (value instanceof BigInteger) {
                    result = Optional.of(FACTORY.numberNode((BigInteger) value));
                } else if (value instanceof BigDecimal) {
                    result = Optional.of(FACTORY.numberNode((BigDecimal) value));
                } else {
                    result = Optional.empty();
                }
            }
            return result;
        }
    }

    private class ListSerializer implements Function<List<Object>, ArrayNode> {

        @Override
        public ArrayNode apply(List<Object> list) {
            return list.stream()
                       .map(valueSerializer)
                       .map(value -> value.orElse(null))
                       .collect(FACTORY::arrayNode, ArrayNode::add, ArrayNode::addAll);
        }

    }

}
