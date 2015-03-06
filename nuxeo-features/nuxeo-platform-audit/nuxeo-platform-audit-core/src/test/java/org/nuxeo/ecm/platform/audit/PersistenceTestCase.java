/*
 * (C) Copyright 2006-2009 Nuxeo SA (http://nuxeo.com/) and others.
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

package org.nuxeo.ecm.platform.audit;

import java.net.URL;

import javax.persistence.EntityManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.persistence.HibernateConfiguration;
import org.nuxeo.ecm.core.persistence.PersistenceProvider;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

/**
 * Base class for persistence
 *
 * @author Stephane Lacoin (Nuxeo EP Software Engineer)
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
public abstract class PersistenceTestCase {

    protected static final Log log = LogFactory.getLog(PersistenceTestCase.class);

    protected PersistenceProvider persistenceProvider;

    protected EntityManager entityManager;

    protected void handleBeforeSetup(HibernateConfiguration config) {
    }

    protected void handleAfterSetup(EntityManager entityManager) {
    }

    @Before
    public void setUp() throws Exception {
        URL resource = getClass().getResource("/hibernate-tests.xml");
        HibernateConfiguration config = HibernateConfiguration.load(resource);
        persistenceProvider = new PersistenceProvider(config);
        handleBeforeSetup(config);
        persistenceProvider.openPersistenceUnit();
        entityManager = persistenceProvider.acquireEntityManagerWithActiveTransaction();
        handleAfterSetup(entityManager);
    }

    @After
    public void tearDown() {
        persistenceProvider.releaseEntityManagerWithRollback(entityManager);
    }

}
