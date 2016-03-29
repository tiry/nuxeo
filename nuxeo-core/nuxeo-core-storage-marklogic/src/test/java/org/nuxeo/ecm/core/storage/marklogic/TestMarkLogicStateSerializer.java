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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ID;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import org.junit.Test;
import org.nuxeo.ecm.core.storage.State;

public class TestMarkLogicStateSerializer {

    private MarkLogicStateSerializer serializer = new MarkLogicStateSerializer();

    @Test
    public void testEmptyState() {
        String json = serializer.apply(new State());
        assertNotNull(json);
        assertEquals("{}", json);
    }

    @Test
    public void testStateWithNullValue() {
        State state = new State();
        state.put("ecm:id", "ID");
        state.put("subState", null);
        String json = serializer.apply(state);
        assertNotNull(json);
        assertEquals(String.format("{\"%s\":\"ID\"}", KEY_ID), json);
    }

    @Test
    public void testStateWithSimpleValue() {
        State state = new State();
        state.put("ecm:id", "ID");
        String json = serializer.apply(state);
        assertNotNull(json);
        assertEquals(String.format("{\"%s\":\"ID\"}", KEY_ID), json);
    }

    @Test
    public void testStateWithSimpleCalendarValue() {
        State state = new State();
        state.put("ecm:id", "ID");
        Calendar creationDate = Calendar.getInstance();
        creationDate.setTimeInMillis(1);
        state.put("dub:creationDate", creationDate);
        String json = serializer.apply(state);
        assertNotNull(json);
        String date = LocalDateTime.ofInstant(Instant.ofEpochMilli(1), ZoneId.systemDefault()).toString();
        assertEquals(String.format("{\"%s\":\"ID\",\"dub:creationDate\":\"%s\"}", KEY_ID, date), json);
    }

    @Test
    public void testStateWithSubState() {
        State state = new State();
        state.put("ecm:id", "ID");
        State subState = new State();
        subState.put("nbValues", 2L);
        subState.put("valuesPresent", false);
        state.put("subState", subState);
        String json = serializer.apply(state);
        assertNotNull(json);
        assertEquals(String.format("{\"%s\":\"ID\",\"subState\":{\"nbValues\":2,\"valuesPresent\":false}}", KEY_ID),
                json);
    }

    @Test
    public void testStateWithList() {
        State state = new State();
        state.put("ecm:id", "ID");
        state.put("nbValues", 2L);
        State state1 = new State();
        state1.put("item", "itemState1");
        state1.put("read", true);
        state1.put("write", true);
        State state2 = new State();
        state2.put("item", "itemState2");
        state2.put("read", true);
        state2.put("write", false);
        state.put("values", new ArrayList<>(Arrays.asList(state1, state2)));
        String json = serializer.apply(state);
        assertNotNull(json);
        assertEquals(
                String.format(
                        "{\"%s\":\"ID\",\"nbValues\":2,\"values\":[{\"item\":\"itemState1\",\"read\":true,\"write\":true},{\"item\":\"itemState2\",\"read\":true,\"write\":false}]}",
                        KEY_ID), json);
    }

    @Test
    public void testStateWithArray() {
        State state = new State();
        state.put("ecm:id", "ID");
        state.put("nbValues", 2L);
        state.put("values", new Long[] { 3L, 4L });
        String json = serializer.apply(state);
        assertNotNull(json);
        assertEquals(String.format("{\"%s\":\"ID\",\"nbValues\":2,\"values\":[3,4]}", KEY_ID), json);
    }

    @Test
    public void testBijunction() {
        State state = new State();
        state.put("ecm:id", "ID");
        state.put("dub:creationDate", Calendar.getInstance());
        State subState = new State();
        subState.put("nbValues", 2L);
        State state1 = new State();
        state1.put("item", "itemState1");
        state1.put("read", true);
        state1.put("write", true);
        State state2 = new State();
        state2.put("item", "itemState2");
        state2.put("read", true);
        state2.put("write", false);
        subState.put("values", new ArrayList<>(Arrays.asList(state1, state2)));
        subState.put("valuesAsArray", new Long[] { 3L, 4L });
        state.put("subState", subState);
        assertEquals(state, serializer.andThen(new MarkLogicStateDeserializer()).apply(state));
    }

}
