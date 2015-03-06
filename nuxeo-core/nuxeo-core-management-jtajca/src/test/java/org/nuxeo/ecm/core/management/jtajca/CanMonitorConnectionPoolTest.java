/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     matic
 */
package org.nuxeo.ecm.core.management.jtajca;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @author matic
 */
@RunWith(FeaturesRunner.class)
@Features(JtajcaManagementFeature.class)
public class CanMonitorConnectionPoolTest {

    @Inject
    JtajcaManagementFeature feature;

    @Inject
    WorkManager works;

    @Inject
    CoreSession repo;

    ConnectionPoolMonitor dbMonitor;

    ConnectionPoolMonitor repoMonitor;

    @Before
    public void lookupMonitors() {
        repoMonitor = feature.lookup(ConnectionPoolMonitor.class,
                Framework.expandVars("repository/${nuxeo.test.vcs.repository}"));
        dbMonitor = feature.lookup(ConnectionPoolMonitor.class,
                Framework.expandVars("jdbc/${nuxeo.test.vcs.database}"));
    }

    @Test
    public void areMonitorsInstalled() {
        isMonitorInstalled(repoMonitor);
        isMonitorInstalled(dbMonitor);
    }

    @Test
    public void indexerWorkDoesNotLeak() throws InterruptedException {
        int repoCount = repoMonitor.getConnectionCount();
        int dbCount = dbMonitor.getConnectionCount();
        DocumentModel doc = repo.createDocumentModel("/", "note", "Note");
        repo.createDocument(doc);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        works.awaitCompletion(10, TimeUnit.SECONDS);
        assertThat(repoCount, is(repoMonitor.getConnectionCount()));
        assertThat(dbCount, is(dbMonitor.getConnectionCount()));

    }

    protected void isMonitorInstalled(ConnectionPoolMonitor monitor) {
        assertThat(monitor, notNullValue());
        monitor.getConnectionCount(); // throw exception is monitor not present
    }

}
