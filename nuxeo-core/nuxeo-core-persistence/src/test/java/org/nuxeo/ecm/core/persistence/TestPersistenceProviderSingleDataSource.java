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

import org.nuxeo.ecm.core.test.NoopRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.datasource.ConnectionHelper;
import org.nuxeo.runtime.test.runner.RuntimeHarness;

@RepositoryConfig(init = TestPersistenceProviderSingleDataSource.SingleDSInit.class)
public class TestPersistenceProviderSingleDataSource extends TestPersistenceProvider {

    public static class SingleDSInit extends NoopRepositoryInit {
        @Override
        public void onDatabaseSetup(RuntimeHarness harness) {
            Framework.getProperties().setProperty(ConnectionHelper.SINGLE_DS, "jdbc/single");
            super.onDatabaseSetup(harness);
        }
    }

}
