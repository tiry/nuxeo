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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import org.junit.Test;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.State.ListDiff;
import org.nuxeo.ecm.core.storage.State.StateDiff;

public class TestMarkLogicStateSerializer extends AbstractTest {

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
        assertEquals("{\"ecm__id\":\"ID\"}", json);
    }

    @Test
    public void testStateWithSimpleValue() {
        State state = new State();
        state.put("ecm:id", "ID");
        String json = serializer.apply(state);
        assertNotNull(json);
        assertEquals("{\"ecm__id\":\"ID\"}", json);
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
        assertEquals(String.format("{\"ecm__id\":\"ID\",\"dub__creationDate\":\"%s\"}", date), json);
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
        assertEquals("{\"ecm__id\":\"ID\",\"subState\":{\"nbValues\":2,\"valuesPresent\":false}}", json);
    }

    @Test
    public void testStateWithList() throws Exception {
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
        assertJSONEquals("serializer/state-with-list.json", json);
    }

    @Test
    public void testStateWithArray() {
        State state = new State();
        state.put("ecm:id", "ID");
        state.put("nbValues", 2L);
        state.put("values", new Long[] { 3L, 4L });
        String json = serializer.apply(state);
        assertNotNull(json);
        assertEquals("{\"ecm__id\":\"ID\",\"nbValues\":2,\"values\":[3,4]}", json);
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

    /*
     * Test serialization of state issued from TestSQLRepositoryAPI#testMarkDirtyForList.
     */
    @Test
    public void testMarkDirtyForList() throws Exception {
        State state = new State();
        state.put("ecm:id", "672f3fc9-38e3-43ec-8b31-f15f6e89f486");
        state.put("ecm:primaryType", "ComplexDoc");
        state.put("ecm:name", "doc");
        state.put("ecm:parentId", "00000000-0000-0000-0000-000000000000");
        State attachedFile = new State();
        ArrayList<State> vignettes = new ArrayList<>();
        State vignette = new State();
        vignette.put("width", 111L);
        vignettes.add(vignette);
        attachedFile.put("vignettes", vignettes);
        state.put("cmpf:attachedFile", attachedFile);
        state.put("ecm:ancestorIds", new Object[] { "00000000-0000-0000-0000-000000000000" });
        state.put("ecm:lifeCyclePolicy", "undefined");
        state.put("ecm:lifeCycleState", "undefined");
        state.put("ecm:majorVersion", 0L);
        state.put("ecm:minorVersion", 0L);
        state.put("ecm:racl", new String[] { "Administrator", "administrators", "members" });
        String json = serializer.apply(state);
        assertNotNull(json);
        assertJSONEquals("serializer/mark-dirty-for-list.json", json);
    }

    @Test
    public void testStateDiffWithListDiff() throws Exception {
        StateDiff diff = new StateDiff();
        StateDiff attachedFile = new StateDiff();
        ListDiff vignettes = new ListDiff();
        vignettes.isArray = false;
        vignettes.diff = new ArrayList<>();
        StateDiff vignette = new StateDiff();
        vignette.put("width", 222L);
        vignettes.diff.add(vignette);
        vignettes.rpush = null;
        attachedFile.put("vignettes", vignettes);
        diff.put("cmpf:attachedFile", attachedFile);
        String json = serializer.apply(diff);
        assertNotNull(json);
        assertJSONEquals("serializer/state-diff-with-list-diff.json", json);
    }

}
