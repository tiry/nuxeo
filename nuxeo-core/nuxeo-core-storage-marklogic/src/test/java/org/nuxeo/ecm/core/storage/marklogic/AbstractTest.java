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

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.BeforeClass;
import org.mockito.Answers;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.impl.DatabaseClientImpl;

public class AbstractTest {

    protected static DatabaseClient client;

    @BeforeClass
    public static void beforeClass() {
        client = Mockito.mock(DatabaseClientImpl.class, Answers.CALLS_REAL_METHODS.get());
    }

    public void assertJSONEquals(String file, String actual) throws Exception {
        assertEquals(readJSONFile(file), new ObjectMapper().reader().readTree(actual).toString());
    }

    public String readFile(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get(this.getClass().getResource("/" + file).toURI())));
    }

    public String readJSONFile(String file) throws Exception {
        return new ObjectMapper().reader().readTree(readFile(file)).toString();
    }

}
