/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     <a href="mailto:grenard@nuxeo.com">Guillaume</a>
 */
package org.nuxeo.ecm.collections.core.worker;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.collections.core.adapter.Collection;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.platform.query.nxql.NXQLQueryBuilder;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 5.9.3
 */
public class RemovedCollectionMemberWork extends AbstractWork {

    public static String QUERY_FOR_COLLECTION_MEMBER_REMOVED = "SELECT * FROM Document WHERE collection:documentIds/* = ?";

    public final static long MAX_RESULT = 50;

    private static final Log log = LogFactory.getLog(RemovedCollectionMemberWork.class);

    private static final long serialVersionUID = 6944563540430297431L;

    public static final String CATEGORY = "removedCollectionMember";

    protected static final String TITLE = "Remove CollectionMember Work";

    protected long offset = 0;

    public RemovedCollectionMemberWork() {
        this.offset = 0;
    }

    protected RemovedCollectionMemberWork(long offset) {
        this.offset = offset;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public String getId() {
        return repositoryName + ":" + docId + ":" + offset;
    }

    private List<DocumentModel> getNextResults() {
        List<DocumentModel> results;
        Object[] parameters = new Object[1];
        parameters[0] = docId;

        String query = NXQLQueryBuilder.getQuery(QUERY_FOR_COLLECTION_MEMBER_REMOVED, parameters, true, false, null);

        results = session.query(query, null, MAX_RESULT, 0,
                MAX_RESULT);
        return results;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    protected void updateDocument(final DocumentModel collection) {
        log.trace(String.format("Worker %s, updating Collection %s", getId(), collection.getTitle()));

        Collection collectionAdapter = collection.getAdapter(Collection.class);
        collectionAdapter.removeDocument(docId);
        session.saveDocument(collection);
    }

    @Override
    public void work() {
        setStatus("Updating");
        if (docId != null) {
            openSystemSession();
            final List<DocumentModel> results = getNextResults();
            final int nbResult = results.size();
            setProgress(new Progress(0, results.size()));
            for (int i = 0; i < nbResult; i++) {
                updateDocument(results.get(i));
                setProgress(new Progress(0, nbResult));
            }

            if (nbResult == MAX_RESULT) {
                setStatus("Rescheduling next work");
                RemovedCollectionMemberWork nextWork = new RemovedCollectionMemberWork(
                        offset + MAX_RESULT);
                nextWork.setDocument(repositoryName, docId);
                WorkManager workManager = Framework.getLocalService(WorkManager.class);
                workManager.schedule(nextWork, WorkManager.Scheduling.IF_NOT_SCHEDULED, true);
                setStatus("Rescheduling Done");
            }
        }
        setStatus("Updating Done");
    }

}
