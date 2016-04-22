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
 *     <a href="mailto:ataillefer@nuxeo.com">Antoine Taillefer</a>
 */
package org.nuxeo.ecm.collections.core.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.LifeCycleConstants;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @since 6.0
 */
public class VisibleCollectionTest extends CollectionTestCase {

    protected static final String COLLECTION_NAME_2 = "testCollection2";

    @Test
    public void testGetVisibleCollection() throws Exception {
        // Create a test doc and add it to 2 collections
        DocumentModel testFile = session.createDocumentModel("/", TEST_FILE_NAME, "File");
        testFile = session.createDocument(testFile);
        collectionManager.addToNewCollection(COLLECTION_NAME, COLLECTION_DESCRIPTION, testFile, session);
        collectionManager.addToNewCollection(COLLECTION_NAME_2, COLLECTION_DESCRIPTION, testFile, session);

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        DocumentModel testCollection = session.getDocument(new PathRef(COLLECTION_FOLDER_PATH + "/" + COLLECTION_NAME));
        DocumentModel testCollection2 = session.getDocument(new PathRef(COLLECTION_FOLDER_PATH + "/"
                + COLLECTION_NAME_2));

        // Check visible collections limited to 1
        testFile = session.getDocument(testFile.getRef());
        List<DocumentModel> collections = collectionManager.getVisibleCollection(testFile, 1, session);
        assertEquals(1, collections.size());
        assertEquals(testCollection2.getId(), collections.get(0).getId());

        // Check visible collections limited to 2
        collections = collectionManager.getVisibleCollection(testFile, 2, session);
        assertEquals(2, collections.size());
        assertTrue(collections.get(0).equals(testCollection2));
        assertTrue(collections.get(1).equals(testCollection));

        // Send one collection to the trash
        testCollection.followTransition(LifeCycleConstants.DELETE_TRANSITION);

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        collections = collectionManager.getVisibleCollection(testFile, 2, session);
        assertEquals(1, collections.size());
        assertEquals(testCollection2.getId(), collections.get(0).getId());

        // Restore collection from the trash
        testCollection.followTransition(LifeCycleConstants.UNDELETE_TRANSITION);

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        collections = collectionManager.getVisibleCollection(testFile, 2, session);
        assertEquals(2, collections.size());
        assertTrue(collections.contains(testCollection));
        assertTrue(collections.contains(testCollection2));

        // Delete one collection permanently
        session.removeDocument(testCollection.getRef());

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        collections = collectionManager.getVisibleCollection(testFile, 1, session);
        assertEquals(1, collections.size());
        assertEquals(testCollection2.getId(), collections.get(0).getId());
    }

}
