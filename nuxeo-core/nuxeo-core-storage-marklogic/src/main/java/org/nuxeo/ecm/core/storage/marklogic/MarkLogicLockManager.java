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

import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_LOCK_CREATED;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_LOCK_OWNER;
import static org.nuxeo.ecm.core.storage.marklogic.MarkLogicHelper.CALENDAR_DESERIALIZER;
import static org.nuxeo.ecm.core.storage.marklogic.MarkLogicHelper.CALENDAR_SERIALIZER;
import static org.nuxeo.ecm.core.storage.marklogic.MarkLogicHelper.KEY_SERIALIZER;

import java.io.IOException;
import java.util.Calendar;

import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.Lock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.marklogic.client.ResourceNotFoundException;
import com.marklogic.client.admin.ExtensionMetadata.ScriptLanguage;
import com.marklogic.client.admin.MethodType;
import com.marklogic.client.extensions.ResourceManager;
import com.marklogic.client.io.BaseHandle;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.marker.ContentHandle;
import com.marklogic.client.io.marker.JSONReadHandle;
import com.marklogic.client.io.marker.JSONWriteHandle;
import com.marklogic.client.util.RequestParameters;

/**
 * Manager to handle {@link Lock} on document server-side.
 *
 * @since 8.3
 */
class MarkLogicLockManager extends ResourceManager {

    public static final MarkLogicResourceExtension DEFINITION;
    static {
        DEFINITION = new MarkLogicResourceExtension();
        DEFINITION.setName("nuxeo-lock-manager");
        DEFINITION.setTitle("Nuxeo Lock Manager");
        DEFINITION.setDescription("Resource extension to handle lock");
        DEFINITION.setProvider("Nuxeo");
        DEFINITION.setVersion("0.1");
        DEFINITION.setScriptLanguage(ScriptLanguage.JAVASCRIPT);
        DEFINITION.addMethodParam(MethodType.PUT);
        DEFINITION.addMethodParam(MethodType.DELETE);
    }

    /**
     * Sets the locks to document with id.
     *
     * @return null if the document was correctly locked, or another lock if it was already locked
     */
    public Lock set(String id, Lock lock) {
        try {
            RequestParameters requestParameters = new RequestParameters();
            requestParameters.add("uri", MarkLogicHelper.ID_FORMATTER.apply(id));
            return getServices().put(requestParameters, new LockHandle(lock), new LockHandle()).get();
        } catch (ResourceNotFoundException e) {
            throw new DocumentNotFoundException(id);
        }
    }

    /**
     * Removes the locks from document with id if owner matchs or is null.
     *
     * @return The previous lock if exists
     */
    public Lock remove(String id, String owner) {
        try {
            RequestParameters requestParameters = new RequestParameters();
            requestParameters.add("uri", MarkLogicHelper.ID_FORMATTER.apply(id));
            requestParameters.add("owner", owner);
            return getServices().delete(requestParameters, new LockHandle()).get();
        } catch (ResourceNotFoundException e) {
            throw new DocumentNotFoundException(id);
        }
    }

    private static class LockHandle extends BaseHandle<byte[], String> implements ContentHandle<Lock>, JSONReadHandle,
            JSONWriteHandle {

        private static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;

        private static final String KEY_OWNER = KEY_SERIALIZER.apply(KEY_LOCK_OWNER);

        private static final String KEY_CREATED = KEY_SERIALIZER.apply(KEY_LOCK_CREATED);

        private Lock lock;

        public LockHandle() {
            super();
            super.setFormat(Format.JSON);
            setResendable(true);
        }

        public LockHandle(Lock lock) {
            this();
            set(lock);
        }

        @Override
        public Lock get() {
            return lock;
        }

        @Override
        public void set(Lock lock) {
            this.lock = lock;
        }

        @Override
        public void setFormat(Format format) {
            if (format != Format.JSON)
                throw new IllegalArgumentException("StateHandle supports the JSON format only");
        }

        @Override
        protected Class<byte[]> receiveAs() {
            return byte[].class;
        }

        @Override
        protected void receiveContent(byte[] bytes) {
            if (bytes == null) {
                this.lock = null;
                return;
            }
            String lockString = new String(bytes, Charsets.UTF_8);
            if ("{}".equals(lockString)) {
                this.lock = null;
            } else {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode lockNode = mapper.readTree(lockString);
                    String owner = lockNode.get(KEY_OWNER).textValue();
                    Calendar created = CALENDAR_DESERIALIZER.apply(lockNode.get(KEY_CREATED).textValue());

                    JsonNode lockFailed = lockNode.get("failed");
                    if (lockFailed != null) {
                        this.lock = new Lock(owner, created, lockFailed.asBoolean(false));
                    } else {
                        this.lock = new Lock(owner, created);
                    }
                } catch (IOException e) {
                    // TODO change that
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        protected String sendContent() {
            if (lock == null) {
                throw new IllegalStateException("No lock to write");
            }
            ObjectNode root = NODE_FACTORY.objectNode();
            root.set(KEY_OWNER, NODE_FACTORY.textNode(lock.getOwner()));
            root.set(KEY_CREATED, NODE_FACTORY.textNode(CALENDAR_SERIALIZER.apply(lock.getCreated())));
            return root.toString();
        }

        @Override
        public String toString() {
            if (lock == null) {
                return null;
            }
            return lock.toString();
        }

    }

}
