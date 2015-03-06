/*
 * (C) Copyright 2006-2007 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * $Id: TestUserService.java 28010 2007-12-07 19:23:44Z fguillaume $
 */

package org.nuxeo.ecm.platform.usermanager;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Test;
import org.nuxeo.ecm.platform.usermanager.UserManager.MatchType;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @author Florent Guillaume
 */
@LocalDeploy({ //
        "org.nuxeo.ecm.platform.usermanager:test-userservice-config.xml" //
})
public class TestUserService extends UserManagerTestCase {

    @Test
    public void testConfig() throws Exception {
        FakeUserManagerImpl fum = (FakeUserManagerImpl) userManager;
        assertEquals("search_only", userManager.getUserListingMode());
        assertEquals("search_oh_yeah", userManager.getGroupListingMode());
        assertEquals(Arrays.asList("tehroot"), fum.defaultAdministratorIds);
        assertEquals(Collections.<String> emptyList(), fum.administratorsGroups);
        assertEquals("defgr", userManager.getDefaultGroup());
        assertEquals("username", userManager.getUserSortField());
        assertEquals("groupname", fum.groupSortField);
        assertEquals("userDirectory", fum.userDirectoryName);
        assertEquals("email", fum.userEmailField);
        // append mode:
        assertEquals(new HashSet<String>(Arrays.asList("firstName", "lastName", "username")),
                fum.getUserSearchFields());
        assertEquals(MatchType.SUBSTRING, fum.userSearchFields.get("username"));
        assertEquals(MatchType.SUBSTRING, fum.userSearchFields.get("firstName"));
        assertEquals(MatchType.SUBSTRING, fum.userSearchFields.get("lastName"));
        assertEquals("groupDirectory", fum.groupDirectoryName);
        assertEquals("members", fum.groupMembersField);
        assertEquals("subGroups", fum.groupSubGroupsField);
        assertEquals("parentGroups", fum.groupParentGroupsField);

        // anonymous user
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("firstName", "Anonymous");
        props.put("lastName", "Coward");
        assertEquals("Guest", fum.getAnonymousUserId());
        assertEquals(props, fum.anonymousUser.getProperties());

        // virtual users
        // custom admin
        assertTrue(fum.virtualUsers.containsKey("MyCustomAdministrator"));

        VirtualUser customAdmin = fum.virtualUsers.get("MyCustomAdministrator");
        assertNotNull(customAdmin);
        assertEquals("MyCustomAdministrator", customAdmin.getId());
        assertEquals(1, customAdmin.getGroups().size());
        assertTrue(customAdmin.getGroups().contains("myAdministrators"));
        assertEquals("secret", customAdmin.getPassword());

        props.clear();
        props.put("firstName", "My Custom");
        props.put("lastName", "Administrator");
        assertEquals(props, customAdmin.getProperties());
        // custom member
        assertTrue(fum.virtualUsers.containsKey("MyCustomMember"));

        VirtualUser customMember = fum.virtualUsers.get("MyCustomMember");
        assertNotNull(customMember);
        assertEquals("MyCustomMember", customMember.getId());
        assertTrue(customMember.getGroups().contains("members"));
        assertTrue(customMember.getGroups().contains("othergroup"));
        assertEquals("secret", customMember.getPassword());

        props.clear();
        props.put("first", "My Custom");
        props.put("last", "Member");
        assertEquals(props, customMember.getProperties());
    }

    @Test
    @LocalDeploy("org.nuxeo.ecm.platform.usermanager:test-userservice-override-config.xml")
    public void testOverride() throws Exception {
        FakeUserManagerImpl fum = (FakeUserManagerImpl) Framework.getService(UserManager.class);
        assertEquals(Arrays.asList("tehroot", "bob", "bobette"), fum.defaultAdministratorIds);
        assertEquals(Arrays.asList("myAdministrators"), fum.administratorsGroups);
        assertEquals("username", userManager.getUserSortField());
        // the rest should be unchanged
        assertEquals("search_only", userManager.getUserListingMode());
        assertEquals("search_oh_yeah", userManager.getGroupListingMode());
        assertEquals("defgr", userManager.getDefaultGroup());
        assertEquals("groupname", fum.groupSortField);
        // anonymous user removed
        assertNull(fum.anonymousUser);

        // custom admin overridden
        assertTrue(fum.virtualUsers.containsKey("MyCustomAdministrator"));

        VirtualUser customAdmin = fum.virtualUsers.get("MyCustomAdministrator");
        assertNotNull(customAdmin);
        assertEquals("MyCustomAdministrator", customAdmin.getId());
        assertEquals(1, customAdmin.getGroups().size());
        assertTrue(customAdmin.getGroups().contains("administrators2"));
        assertEquals("secret2", customAdmin.getPassword());

        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("first", "My Custom 2");
        props.put("last", "Administrator 2");
        assertEquals(props, customAdmin.getProperties());
        // custom member removed
        assertFalse(fum.virtualUsers.containsKey("MyCustomMember"));
        assertNull(fum.virtualUsers.get("MyCustomMember"));
    }

    @Test
    public void testValidatePassword() throws Exception {
        FakeUserManagerImpl fum = (FakeUserManagerImpl) userManager;
        assertTrue(fum.validatePassword(""));
        deployTestContrib("org.nuxeo.ecm.platform.usermanager", "test-userservice-override-config.xml");
        userManager = Framework.getService(UserManager.class);
        fum = (FakeUserManagerImpl) Framework.getService(UserManager.class);
        assertFalse(fum.validatePassword(""));
        assertFalse(fum.validatePassword("azerty"));
        assertFalse(fum.validatePassword("az\u00e9rtyyy"));
        assertFalse(fum.validatePassword("aZeRtYuIoP"));
        assertFalse(fum.validatePassword("aZ1\u00e9R2tY3"));
        assertFalse(fum.validatePassword("aZE1RTY2"));
        assertTrue(fum.validatePassword("aZ1eR2tY3"));
    }

}
