/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Stephane Lacoin
 */
package org.nuxeo.ecm.core.test;

import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.repository.RepositoryFactory;
import org.junit.runners.model.FrameworkMethod;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.test.annotations.TransactionalConfig;
import org.nuxeo.runtime.test.runner.ContainerFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

@RepositoryConfig(cleanup = Granularity.METHOD)
@Features(ContainerFeature.class)
public class TransactionalFeature extends SimpleFeature {

    protected TransactionalConfig txConfig;

    protected RepositoryConfig repoConfig;

    protected boolean txStarted;

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        txConfig = runner.getConfig(TransactionalConfig.class);
        repoConfig = runner.getConfig(RepositoryConfig.class);
    }

    @Override
    public void beforeRun(FeaturesRunner runner) throws Exception {
        if (!txConfig.autoStart()) {
            return;
        }
        txStarted = TransactionHelper.startTransaction();
    }

    @Override
    public void afterRun(FeaturesRunner runner) throws Exception {
        if (txStarted == false) {
            LogFactory.getLog(TransactionalFeature.class)
                    .warn("Committing a transaction for your, please do it yourself");
        }
        TransactionHelper.setTransactionRollbackOnly();
        TransactionHelper.commitOrRollbackTransaction();
    }

    @Override
    public void beforeMethodRun(FeaturesRunner runner, FrameworkMethod method, Object test) throws Exception {
        if (!Granularity.METHOD.equals(repoConfig.cleanup())) {
            return;
        }
        if (!txStarted) {
            return;
        }
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

    @Override
    public void afterMethodRun(FeaturesRunner runner, FrameworkMethod method, Object test) throws Exception {
        if (!Granularity.METHOD.equals(repoConfig.cleanup())) {
            return;
        }
        if (!txStarted) {
            return;
        }
        TransactionHelper.setTransactionRollbackOnly();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }


}
