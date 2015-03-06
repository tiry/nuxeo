package org.nuxeo.ecm.directory.sql;

import org.nuxeo.ecm.core.redis.RedisFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.LocalDeploy;

@Features(RedisFeature.class)
@LocalDeploy("org.nuxeo.ecm.core.redis:sql-directory-redis-cache-config.xml")
public class TestRedisCachedSQLDirectory extends TestCachedSQLDirectory {

}
