/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */

package org.nuxeo.ecm.core.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.DateIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.EnumConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.LengthConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NumericIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.PatternConstraint;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.NXRuntimeTestCase;

public class TestSchemaLoaderRestriction extends NXRuntimeTestCase {

    public static final String NS_XSD = "http://www.w3.org/2001/XMLSchema";

    private Schema schema;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        deployBundle("org.nuxeo.ecm.core.schema");
        SchemaManager typeMgr = Framework.getLocalService(SchemaManager.class);
        XSDLoader reader = new XSDLoader((SchemaManagerImpl) typeMgr);
        URL url = getResource("schema/testrestriction.xsd");
        this.schema = reader.loadSchema("testrestriction", "", url);
    }

    @Test
    public void testBinaryRestrictions() throws Exception {
        Field field = this.schema.getField("binaryConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(1, constraints.size());
        assertTrue(constraints.contains(NotNullConstraint.get()));
    }

    @Test
    public void testBooleanRestrictions() throws Exception {
        Field field = this.schema.getField("booleanConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(1, constraints.size());
        assertTrue(constraints.contains(NotNullConstraint.get()));
    }

    @Test
    public void testStringRestrictions() throws Exception {
        Field field = this.schema.getField("stringConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(4, constraints.size());
        assertTrue(constraints.contains(NotNullConstraint.get()));
        assertTrue(constraints.contains(new PatternConstraint("[^3]*")));
        assertTrue(constraints.contains(new LengthConstraint(2, 4)));
        assertTrue(constraints.contains(new EnumConstraint(Arrays.asList("1",
                "1234", "22", "333", "4444", "55555"))));
    }

    @Test
    public void testNumericRestrictions() throws Exception {
        Field field = this.schema.getField("decimalConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(4, constraints.size());
        assertTrue(constraints.contains(NotNullConstraint.get()));
        assertTrue(constraints.contains(new PatternConstraint("[^6]*")));
        assertTrue(constraints.contains(new EnumConstraint(Arrays.asList(
                "2014.15", "2015.1555", "2016.15", "2017.15", "2017.15555",
                "2017.155555", "2018.15"))));
        assertTrue(constraints.contains(new NumericIntervalConstraint(
                2015.001d, true, 2017.999d, true)));
    }

    @Test
    public void testDateRestrictions() throws Exception {
        Field field = this.schema.getField("dateConstraints");
        assertNotNull(field);
        Set<Constraint> constraints = field.getConstraints();
        assertEquals(2, constraints.size());
        assertTrue(constraints.contains(NotNullConstraint.get()));
        assertTrue(constraints.contains(new DateIntervalConstraint(
                new GregorianCalendar(2015, 0, 1), true, new GregorianCalendar(
                        2016, 11, 31), true)));
    }

    @Test
    public void testMaxLength() throws Exception {
        Field field = this.schema.getField("stringConstraints");
        assertEquals(4, field.getMaxLength());
    }

    @Test
    public void testNillable() throws Exception {
        doTestNillableCase("shouldNotBeNullButCanBeNull1", true);
        doTestNillableCase("shouldNotBeNullButCanBeNull2", true);
        doTestNillableCase("shouldNotBeNullButCanBeNull3", true);
        doTestNillableCase("canBeNull1", true);
        doTestNillableCase("canBeNull2", true);
        doTestNillableCase("canBeNull3", true);
        doTestNillableCase("canBeNull4", true);
        doTestNillableCase("cannotBeNull1", false);
        doTestNillableCase("cannotBeNull2", false);
    }

    private void doTestNillableCase(String name, boolean expected) {
        Field field = this.schema.getField(name);
        assertEquals(!expected,
                field.getConstraints().contains(NotNullConstraint.get()));
        assertEquals(expected, field.isNillable());
    }

}
