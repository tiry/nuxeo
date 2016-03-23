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

import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_PROXY;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_NAME;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PARENT_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PROXY_IDS;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PROXY_TARGET_ID;
import static org.nuxeo.ecm.core.storage.marklogic.MarkLogicQueryBuilder.QUERY;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.resource.spi.ConnectionManager;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.State.StateDiff;
import org.nuxeo.ecm.core.storage.dbs.DBSExpressionEvaluator;
import org.nuxeo.ecm.core.storage.dbs.DBSRepositoryBase;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.document.DocumentMetadataPatchBuilder.PatchHandle;
import com.marklogic.client.document.DocumentPage;
import com.marklogic.client.document.DocumentRecord;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.client.io.marker.StructureWriteHandle;
import com.marklogic.client.query.QueryDefinition;

/**
 * MarkLogic implementation of a {@link Repository}.
 *
 * @since 8.2
 */
public class MarkLogicRepository extends DBSRepositoryBase {

    private static final Log log = LogFactory.getLog(MarkLogicRepository.class);

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private static final Function<String, String> ID_FORMATTER = id -> String.format("/%s.json", id);

    public static final String DB_DEFAULT = "nuxeo";

    protected DatabaseClient markLogicClient;

    public MarkLogicRepository(ConnectionManager cm, MarkLogicRepositoryDescriptor descriptor) {
        super(cm, descriptor.name, descriptor.getFulltextDescriptor());
        markLogicClient = newMarkLogicClient(descriptor);
        initRepository();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        markLogicClient.release();
    }

    // used also by unit tests
    public static DatabaseClient newMarkLogicClient(MarkLogicRepositoryDescriptor descriptor) {
        String host = descriptor.host;
        Integer port = descriptor.port;
        if (StringUtils.isBlank(host) || port == null) {
            throw new NuxeoException("Missing <host> or <port> in MarkLogic repository descriptor");
        }
        String dbname = StringUtils.defaultIfBlank(descriptor.dbname, DB_DEFAULT);
        String user = descriptor.user;
        String password = descriptor.password;
        if (StringUtils.isNotBlank(user) && StringUtils.isNotBlank(password)) {
            return DatabaseClientFactory.newClient(host, port, dbname, user, password, Authentication.DIGEST);
        }
        return DatabaseClientFactory.newClient(host, port, dbname);
    }

    protected void initRepository() {
        initRoot();
    }

    @Override
    protected void initBlobsPaths() {
        // throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public String generateNewId() {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public State readState(String id) {
        if (log.isTraceEnabled()) {
            log.trace("MarkLogic: READ " + id);
        }
        return markLogicClient.newJSONDocumentManager().read(ID_FORMATTER.apply(id), new StateHandle()).get();
    }

    @Override
    public List<State> readStates(List<String> ids) {
        if (log.isTraceEnabled()) {
            log.trace("MarkLogic: READ " + ids);
        }
        String[] markLogicIds = ids.stream().map(ID_FORMATTER).toArray(String[]::new);
        DocumentPage page = markLogicClient.newJSONDocumentManager().read(markLogicIds);
        return StreamSupport.stream(page.spliterator(), false)
                            .map(document -> document.getContent(new StateHandle()).get())
                            .collect(Collectors.toList());
    }

    @Override
    public void createState(State state) {
        String id = state.get(KEY_ID).toString();
        if (log.isTraceEnabled()) {
            log.trace("MarkLogic: CREATE " + id + ": " + state);
        }
        markLogicClient.newJSONDocumentManager().write(ID_FORMATTER.apply(id), new StateHandle(state));
    }

    @Override
    public void updateState(String id, StateDiff diff) {
        JSONDocumentManager docManager = markLogicClient.newJSONDocumentManager();
        PatchHandle patch = new MarkLogicUpdateBuilder(docManager::newPatchBuilder).apply(diff);
        if (log.isTraceEnabled()) {
            log.trace("MarkLogic: UPDATE " + id + ": " + patch.toString());
        }
        docManager.patch(ID_FORMATTER.apply(id), patch);
    }

    @Override
    public void deleteStates(Set<String> ids) {
        if (log.isTraceEnabled()) {
            log.trace("MarkLogic: DELETE " + ids);
        }
        String[] markLogicIds = ids.stream().map(ID_FORMATTER).toArray(String[]::new);
        markLogicClient.newJSONDocumentManager().delete(markLogicIds);
    }

    @Override
    public State readChildState(String parentId, String name, Set<String> ignored) {
        StructureWriteHandle query = getChildQuery(parentId, name, ignored);
        return findOne(query);
    }

    @Override
    public boolean hasChild(String parentId, String name, Set<String> ignored) {
        StructureWriteHandle query = getChildQuery(parentId, name, ignored);
        return exist(query);
    }

    private StructureWriteHandle getChildQuery(String parentId, String name, Set<String> ignored) {
        ObjectNode root = FACTORY.objectNode();
        ObjectNode query = FACTORY.objectNode();
        query.set(KEY_PARENT_ID, FACTORY.textNode(parentId));
        query.set(KEY_NAME, FACTORY.textNode(name));
        addIgnoredIds(query, ignored);
        root.set(QUERY, query);
        return new JacksonHandle(root);
    }

    private void addIgnoredIds(ObjectNode query, Set<String> ignored) {
        if (!ignored.isEmpty()) {
            ArrayNode array = ignored.stream()
                                     .map(FACTORY::textNode)
                                     .reduce(FACTORY.arrayNode(), ArrayNode::add, ArrayNode::addAll);
            ObjectNode notIn = FACTORY.objectNode();
            notIn.set(KEY_ID, array);
            query.set(KEY_ID, notIn);
        }
    }

    @Override
    public List<State> queryKeyValue(String key, Object value, Set<String> ignored) {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public List<State> queryKeyValue(String key1, Object value1, String key2, Object value2, Set<String> ignored) {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public void queryKeyValueArray(String key, Object value, Set<String> ids, Map<String, String> proxyTargets,
            Map<String, Object[]> targetProxies) {
        // TODO retrieve only some field
        ObjectNode root = FACTORY.objectNode();
        ObjectNode query = FACTORY.objectNode();
        query.set(key, FACTORY.textNode(value.toString()));
        root.set(QUERY, query);
        if (log.isTraceEnabled()) {
            logQuery(query);
        }
        try (DocumentPage page = markLogicClient.newJSONDocumentManager().search(init(query), 0)) {
            for (DocumentRecord record : page) {
                State state = record.getContent(new StateHandle()).get();
                String id = (String) state.get(KEY_ID);
                ids.add(id);
                if (proxyTargets != null && TRUE.equals(state.get(KEY_IS_PROXY))) {
                    String targetId = (String) state.get(KEY_PROXY_TARGET_ID);
                    proxyTargets.put(id, targetId);
                }
                if (targetProxies != null) {
                    Object[] proxyIds = (Object[]) state.get(KEY_PROXY_IDS);
                    if (proxyIds != null) {
                        targetProxies.put(id, proxyIds);
                    }
                }
            }
        }
    }

    @Override
    public boolean queryKeyValuePresence(String key, String value, Set<String> ignored) {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public PartialList<Map<String, Serializable>> queryAndFetch(DBSExpressionEvaluator evaluator,
            OrderByClause orderByClause, boolean distinctDocuments, int limit, int offset, int countUpTo) {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public Lock getLock(String id) {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public Lock setLock(String id, Lock lock) {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public Lock removeLock(String id, String owner) {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public void closeLockManager() {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public void clearLockManagerCaches() {
        throw new IllegalStateException("Not implemented yet");
    }

    @Override
    public void markReferencedBinaries() {
        throw new IllegalStateException("Not implemented yet");
    }

    private boolean exist(StructureWriteHandle query) {
        if (log.isTraceEnabled()) {
            logQuery(query);
        }
        return markLogicClient.newQueryManager().findOne(init(query)) != null;
    }

    private State findOne(StructureWriteHandle query) {
        if (log.isTraceEnabled()) {
            logQuery(query);
        }
        try (DocumentPage page = markLogicClient.newJSONDocumentManager().search(init(query), 0)) {
            return page.nextContent(new StateHandle()).get();
        }
    }

    private QueryDefinition init(StructureWriteHandle query) {
        return markLogicClient.newQueryManager().newRawQueryByExampleDefinition(query);
    }

    private void logQuery(StructureWriteHandle query) {
        log.trace("MarkLogic: QUERY " + query);
    }

}
