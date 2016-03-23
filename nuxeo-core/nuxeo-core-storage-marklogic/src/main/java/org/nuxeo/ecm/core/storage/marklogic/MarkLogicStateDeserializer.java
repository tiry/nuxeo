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

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import org.nuxeo.ecm.core.storage.State;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * MarkLogic Deserializer to convert {@link String} into {@link State}.
 *
 * @since 8.2
 */
class MarkLogicStateDeserializer implements Function<String, State> {

    public static final MarkLogicStateDeserializer DESERIALIZER = new MarkLogicStateDeserializer();

    public static final String DATE_REGEXP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}?\\.\\d{3}?";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Function<JsonNode, State> stateDeserializer;

    private final Function<JsonNode, Serializable> valueDeserializer;

    private final Function<ArrayNode, Serializable> listDeserializer;

    public MarkLogicStateDeserializer() {
        this.stateDeserializer = new StateDeserializer();
        this.valueDeserializer = new ValueDeserializer();
        this.listDeserializer = new ListDeserializer();
    }

    @Override
    public State apply(String s) {
        try {
            JsonNode object = objectMapper.readTree(s);
            return stateDeserializer.apply(object);
        } catch (IOException e) {
            // TODO change that
            throw new RuntimeException(e);
        }
    }

    private class StateDeserializer implements Function<JsonNode, State> {

        @Override
        public State apply(JsonNode jsonNode) {
            State state = new State(jsonNode.size());
            Iterator<Entry<String, JsonNode>> elements = jsonNode.fields();
            while (elements.hasNext()) {
                Entry<String, JsonNode> element = elements.next();
                state.put(element.getKey(), valueDeserializer.apply(element.getValue()));
            }
            return state;
        }

    }

    private class ValueDeserializer implements Function<JsonNode, Serializable> {

        @Override
        public Serializable apply(JsonNode jsonNode) {
            Serializable result;
            if (jsonNode.isArray()) {
                result = listDeserializer.apply((ArrayNode) jsonNode);
            } else if (jsonNode.isObject()) {
                result = stateDeserializer.apply(jsonNode);
            } else {
                // Test if it's an ISO-8601 date
                // TODO change this check
                if (jsonNode.isTextual() && jsonNode.textValue().matches(DATE_REGEXP)) {
                    LocalDateTime dateTime = LocalDateTime.parse(jsonNode.textValue());
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()));
                    result = cal;
                } else if (jsonNode.isBoolean()) {
                    result = jsonNode.booleanValue();
                } else if (jsonNode.isNumber()) {
                    result = jsonNode.numberValue();
                } else {
                    result = jsonNode.textValue();
                }
            }
            return result;
        }

    }

    private class ListDeserializer implements Function<ArrayNode, Serializable> {

        @Override
        public Serializable apply(ArrayNode arrayNode) {
            Serializable result;
            if (arrayNode.size() == 0) {
                result = null;
            } else if (arrayNode.get(0).isObject()) {
                ArrayList<Serializable> l = new ArrayList<>(arrayNode.size());
                for (JsonNode node : arrayNode) {
                    l.add(stateDeserializer.apply(node));
                }
                result = l;
            } else {
                // turn the list into a properly-typed array
                Class<?> klass = StreamSupport.stream(arrayNode.spliterator(), false)
                                              .map(this::scalarToSerializableClass)
                                              .findFirst()
                                              .orElse(Object.class);
                result = StreamSupport.stream(arrayNode.spliterator(), false)
                                      .map(valueDeserializer)
                                      .toArray(size -> (Object[]) Array.newInstance(klass, size));
            }
            return result;
        }

        private Class<?> scalarToSerializableClass(JsonNode jsonNode) {
            Class<?> result;
            // TODO change this check
            if (jsonNode.isTextual() && jsonNode.textValue().matches(DATE_REGEXP)) {
                result = Calendar.class;
            } else if (jsonNode.isBoolean()) {
                result = Boolean.class;
            } else if (jsonNode.isNumber()) {
                result = jsonNode.numberValue().getClass();
            } else {
                result = String.class;
            }
            return result;
        }

    }

}
