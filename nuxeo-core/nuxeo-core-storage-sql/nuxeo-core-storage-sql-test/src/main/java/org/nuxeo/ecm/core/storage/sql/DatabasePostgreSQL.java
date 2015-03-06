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
 *     Florent Guillaume
 */

package org.nuxeo.ecm.core.storage.sql;

import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.nuxeo.runtime.api.Framework;

/**
 * @author Florent Guillaume
 */
public class DatabasePostgreSQL extends DatabaseHelper {

    public static DatabaseHelper INSTANCE = new DatabasePostgreSQL();

    private static final String DEF_SERVER = "localhost";

    private static final String DEF_PORT = "5432";

    private static final String DEF_USER = "nuxeo";

    private static final String DEF_PASSWORD = "nuxeo";

    private static final String DRIVER = "org.postgresql.Driver";

    @Override
    protected void setProperties() {
        setProperty(DATABASE_PROPERTY, DEFAULT_DATABASE_NAME);
        setProperty(SERVER_PROPERTY, DEF_SERVER);
        setProperty(PORT_PROPERTY, DEF_PORT);
        setProperty(USER_PROPERTY, DEF_USER);
        setProperty(PASSWORD_PROPERTY, DEF_PASSWORD);
        setProperty(DRIVER_PROPERTY, DRIVER);
        setProperty(URL_PROPERTY, "jdbc:postgresql://%s:%s/%s", SERVER_PROPERTY, PORT_PROPERTY, DATABASE_PROPERTY);
        setProperty(ID_TYPE_PROPERTY, DEF_ID_TYPE);
    }

    @Override
    public void initDatabase(Connection connection) throws Exception {
        doOnAllTables(connection, null, "public", "DROP TABLE \"%s\" CASCADE");
        try (Statement st = connection.createStatement()) {
            executeSql(st, "DROP SEQUENCE IF EXISTS hierarchy_seq");
        }
    }

    @Override
    public RepositoryDescriptor getRepositoryDescriptor() {
        RepositoryDescriptor descriptor = new RepositoryDescriptor();
        descriptor.xaDataSourceName = "org.postgresql.xa.PGXADataSource";
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("ServerName", Framework.getProperty(SERVER_PROPERTY));
        properties.put("PortNumber", Framework.getProperty(PORT_PROPERTY));
        properties.put("DatabaseName", Framework.getProperty(DATABASE_PROPERTY));
        properties.put("User", Framework.getProperty(USER_PROPERTY));
        properties.put("Password", Framework.getProperty(PASSWORD_PROPERTY));
        descriptor.properties = properties;
        descriptor.setFulltextAnalyzer("french");
        descriptor.setPathOptimizationsEnabled(true);
        descriptor.setAclOptimizationsEnabled(true);
        descriptor.idType = Framework.getProperty(ID_TYPE_PROPERTY);
        return descriptor;
    }

    @Override
    public boolean supportsClustering() {
        return true;
    }

    @Override
    public boolean supportsSoftDelete() {
        return true;
    }

    @Override
    public boolean supportsSequenceId() {
        return true;
    }

    @Override
    public boolean supportsArrayColumns() {
        return true;
    }

}
