package org.nuxeo.ecm.directory.ldap;

import org.nuxeo.ecm.core.redis.RedisFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.LocalDeploy;

@Features(RedisFeature.class)
@LocalDeploy( "org.nuxeo.ecm.directory.ldap:ldap-directory-redis-cache-config.xml")
public class TestRedisCachedLDAPSession extends TestCachedLDAPSession {

}
