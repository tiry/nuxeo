/*
 * (C) Copyright 2007 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id: TestRegisterPlacefulService.java 13110 2007-03-01 17:25:47Z rspivak $
 */
package org.nuxeo.ecm.platform.ec.notification;

import java.io.Serializable;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.ec.notification.email.EmailHelper;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationService;
import org.nuxeo.ecm.platform.notification.api.Notification;
import org.nuxeo.ecm.platform.notification.api.NotificationManager;
import org.nuxeo.ecm.platform.notification.api.NotificationRegistry;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.NXRuntimeTestCase;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @author <a href="mailto:rspivak@nuxeo.com">Ruslan Spivak</a>
 */
@Features(CoreFeature.class)
@Deploy({ "org.nuxeo.ecm.platform.notification.api", "org.nuxeo.ecm.platform.notification.core" })
@LocalDeploy("org.nuxeo.ecm.platform.notification.core:notifications.properties")
public class TestRegisterNotificationService extends NXRuntimeTestCase {

    NotificationService notificationService;

    NotificationRegistry notificationRegistry;

    EmailHelper mailHelper = new EmailHelper();

    @Test
    @LocalDeploy("org.nuxeo.ecm.platform.notification.core:notification-contrib.xml")
    public void testRegistration() throws Exception {
        List<Notification> notifications = getService().getNotificationsForEvents("testEvent");

        assertEquals(1, notifications.size());

        Notification notif = notifications.get(0);
        assertEquals("email", notif.getChannel());
        assertEquals(false, notif.getAutoSubscribed());
        assertEquals("section", notif.getAvailableIn());
        // assertEquals(true, notif.getEnabled());
        assertEquals("Test Notification Label", notif.getLabel());
        assertEquals("Test Notification Subject", notif.getSubject());
        assertEquals("Test Notification Subject Template", notif.getSubjectTemplate());
        assertEquals("test-template", notif.getTemplate());
        assertEquals("NotificationContext['exp1']", notif.getTemplateExpr());

        Map<String, Serializable> infos = new HashMap<String, Serializable>();
        infos.put("exp1", "myDynamicTemplate");
        String template = mailHelper.evaluateMvelExpresssion(notif.getTemplateExpr(), infos);
        assertEquals("myDynamicTemplate", template);

        notifications = getRegistry().getNotificationsForSubscriptions("section");
        assertEquals(1, notifications.size());

        URL newModifTemplate = NotificationService.getTemplateURL("test-template");
        assertTrue(newModifTemplate.getFile().endsWith("templates/test-template.ftl"));

    }

    @Test
    public void testRegistrationDisabled() throws Exception {
        deployTestContrib("org.nuxeo.ecm.platform.notification.core", "notification-contrib-disabled.xml");
        List<Notification> notifications = getService().getNotificationsForEvents("testEvent");

        assertEquals(0, notifications.size());
    }

    @Test
    public void testRegistrationOverrideWithDisabled() throws Exception {
        deployTestContrib("org.nuxeo.ecm.platform.notification.core", "notification-contrib.xml");
        List<Notification> notifications = getService().getNotificationsForEvents("testEvent");

        assertEquals(1, notifications.size());
        deployTestContrib("org.nuxeo.ecm.platform.notification.core", "notification-contrib-disabled.xml");
        notifications = getService().getNotificationsForEvents("testEvent");

        assertEquals(0, notifications.size());
    }

    @Test
    public void testRegistrationOverride() throws Exception {
        deployTestContrib("org.nuxeo.ecm.platform.notification.core", "notification-contrib.xml");
        deployTestContrib("org.nuxeo.ecm.platform.notification.core", "notification-contrib-overridden.xml");

        List<Notification> notifications = getService().getNotificationsForEvents("testEvent");
        assertEquals(0, notifications.size());

        notifications = getService().getNotificationsForEvents("testEvent-ov");
        assertEquals(1, notifications.size());

        Notification notif = notifications.get(0);
        assertEquals("email-ov", notif.getChannel());
        assertEquals(true, notif.getAutoSubscribed());
        assertEquals("folder", notif.getAvailableIn());
        // assertEquals(true, notif.getEnabled());
        assertEquals("Test Notification Label-ov", notif.getLabel());
        assertEquals("Test Notification Subject-ov", notif.getSubject());
        assertEquals("Test Notification Subject Template-ov", notif.getSubjectTemplate());
        assertEquals("test-template-ov", notif.getTemplate());
        assertEquals("NotificationContext['exp1-ov']", notif.getTemplateExpr());

        notifications = getRegistry().getNotificationsForSubscriptions("section");
        assertEquals(0, notifications.size());

        notifications = getRegistry().getNotificationsForSubscriptions("folder");
        assertEquals(0, notifications.size());

        URL newModifTemplate = NotificationService.getTemplateURL("test-template");
        assertTrue(newModifTemplate.getFile().endsWith("templates/test-template-ov.ftl"));
    }

    @Test
    @LocalDeploy("org.nuxeo.ecm.platform.notification.core:notification-contrib.xml")
    public void testExpandVarsInGeneralSettings() throws Exception {

        assertEquals("http://localhost:8080/nuxeo/", getService().getServerUrlPrefix());
        assertEquals("[Nuxeo5]", getService().getEMailSubjectPrefix());

        // this one should not be expanded
        assertEquals("java:/Mail", getService().getMailSessionJndiName());

        deployTestContrib("org.nuxeo.ecm.platform.notification.core", "notification-contrib-overridden.xml");

        assertEquals("http://testServerPrefix/nuxeo", getService().getServerUrlPrefix());
        assertEquals("testSubjectPrefix", getService().getEMailSubjectPrefix());

        // this one should not be expanded
        assertEquals("${not.existing.property}", getService().getMailSessionJndiName());
    }

    @Test
    @LocalDeploy({ "org.nuxeo.ecm.platform.notification.core:notification-veto-contrib.xml",
            "org.nuxeo.ecm.platform.notification.core:notification-veto-contrib-overridden.xml" })
    public void testVetoRegistration() throws Exception {
        Collection<NotificationListenerVeto> vetos = getService().getNotificationVetos();
        assertEquals(2, vetos.size());
        assertEquals("org.nuxeo.ecm.platform.ec.notification.veto.NotificationVeto1",
                getService().getNotificationListenerVetoRegistry().getVeto("veto1").getClass().getCanonicalName());
        assertEquals("org.nuxeo.ecm.platform.ec.notification.veto.NotificationVeto20",
                getService().getNotificationListenerVetoRegistry().getVeto("veto2").getClass().getCanonicalName());

    }

    public NotificationService getService() {
        if (notificationService == null) {
            notificationService = (NotificationService) Framework.getLocalService(NotificationManager.class);
        }
        return notificationService;
    }

    public NotificationRegistry getRegistry() {
        if (notificationRegistry == null) {
            notificationRegistry = getService().getNotificationRegistry();
        }
        return notificationRegistry;
    }

}
