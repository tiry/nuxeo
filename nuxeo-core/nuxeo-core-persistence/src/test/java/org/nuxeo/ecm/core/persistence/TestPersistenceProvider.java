/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.persistence;

import javax.persistence.EntityManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.NoopRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.persistence")
@RepositoryConfig(init = NoopRepositoryInit.class)
@LocalDeploy("org.nuxeo.ecm.core.persistence:OSGI-INF/test-persistence-config.xml")
public class TestPersistenceProvider {

    protected PersistenceProvider persistenceProvider;


    @Before
    public void activatePersistenceProvider() {
        ClassLoader last = Thread.currentThread().getContextClassLoader();
        try {
            // needs context classloader for Hibernate to find the
            // META-INF/persistence.xml file
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            PersistenceProviderFactory persistenceProviderFactory = Framework
                    .getLocalService(PersistenceProviderFactory.class);
            persistenceProvider = persistenceProviderFactory.newProvider("nxtest");
            persistenceProvider.openPersistenceUnit();
        } finally {
            Thread.currentThread().setContextClassLoader(last);
        }
    }

    @After
    public void deactivatePersistenceProvider() {
        if (persistenceProvider != null) {
            persistenceProvider.closePersistenceUnit();
            persistenceProvider = null;
        }
    }

    @Test
    public void testBase() throws Exception {
        EntityManager em = persistenceProvider.acquireEntityManager();
        DummyEntity entity = new DummyEntity("1");
        em.persist(entity);
        em.flush();
        em.clear();
        em.close();
    }

    @Test
    public void testAcquireTwiceInSameTx() throws Exception {
        EntityManager em;
        em = persistenceProvider.acquireEntityManager();
        em.persist(new DummyEntity("3"));
        em.flush();
        em.clear();
        em.close();
        em = persistenceProvider.acquireEntityManager();
        em.persist(new DummyEntity("4"));
        em.flush();
        em.clear();
        em.close();
    }

}
