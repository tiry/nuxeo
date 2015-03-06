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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Florent Guillaume
 */
public class DatabaseH2 extends DatabaseHelper {

    public static final DatabaseHelper INSTANCE = new DatabaseH2();

    private static final Log log = LogFactory.getLog(DatabaseH2.class);

    /** This directory will be deleted and recreated. */
    protected static final String DIRECTORY = "target";

    protected static final String URL2_PROPERTY = URL_PROPERTY + "2";

    protected static final String DATABASE2_PROPERTY = DATABASE_PROPERTY + "2";

    protected static final String REPOSITORY2_PROPERTY = REPOSITORY_PROPERTY + "2";

    protected static final String URL_FORMAT = "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false";

    @Override
    protected void setProperties() {
        setProperty(USER_PROPERTY, "");
        setProperty(PASSWORD_PROPERTY, "");
        setProperty(DRIVER_PROPERTY, "org.h2.Driver");
        setProperty(URL_PROPERTY, URL_FORMAT, DATABASE_PROPERTY);
        setProperty(FULLTEXT_ANALYZER_PROPERTY, "org.apache.lucene.analysis.fr.FrenchAnalyzer");
    }

    @Override
    public void initDatabase(Connection connection) throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT 1");
        }
    }

    protected String getId() {
        return "nuxeo";
    }

    @Override
    public void tearDown() throws SQLException {
        if (owner == null) {
            return;
        }
        try {
            tearDownDatabase(getProperty(URL_PROPERTY));
        } finally {
            super.tearDown();
        }
    }

    protected void tearDownDatabase(String url) throws SQLException {
        Connection connection = DriverManager.getConnection(url, getProperty(USER_PROPERTY),
                getProperty(PASSWORD_PROPERTY));
        try {
            Statement st = connection.createStatement();
            try {
                String sql = "SHUTDOWN";
                log.trace(sql);
                st.execute(sql);
            } finally {
                st.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public RepositoryDescriptor getRepositoryDescriptor() {
        RepositoryDescriptor descriptor = new RepositoryDescriptor();
        descriptor.xaDataSourceName = "org.h2.jdbcx.JdbcDataSource";
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("URL", getProperty(URL_PROPERTY));
        properties.put("User", getProperty(USER_PROPERTY));
        properties.put("Password", getProperty(PASSWORD_PROPERTY));
        descriptor.properties = properties;
        return descriptor;
    }

    @Override
    public boolean supportsClustering() {
        return true;
    }

}
