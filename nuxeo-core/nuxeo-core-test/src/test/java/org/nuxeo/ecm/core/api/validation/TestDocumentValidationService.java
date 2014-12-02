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

package org.nuxeo.ecm.core.api.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.ConstraintViolation;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NumericIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.PatternConstraint;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Deploy({ "org.nuxeo.ecm.core.test.tests:OSGI-INF/test-validation-service-contrib.xml" })
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestDocumentValidationService {

    private static final String SIMPLE_FIELD = "vs:groupCode";

    private static final String COMPLEX_FIELD = "vs:manager";

    private static final String LIST_FIELD = "vs:roles";

    private static final String COMPLEX_LIST_FIELD = "vs:users";

    private static final String FIELD = "groupCode";

    private static final String SCHEMA = "validationSample";

    @Inject
    protected CoreSession session;

    @Inject
    protected DocumentValidationService validator;

    @Inject
    protected SchemaManager metamodel;

    DocumentModel doc;

    @Before
    public void setUp() {
        doc = session.createDocumentModel("/", "doc1", "ValidatedUserGroup");
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
    }

    @Test
    public void testServiceFetching() {
        assertNotNull(validator);
    }

    @Test
    public void testDocumentWithoutViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        checkOk(validator.validate(doc));
    }

    @Test
    public void testDocumentWithViolation1() {
        checkNotNullOnGroupCode(validator.validate(doc));
    }

    @Test
    public void testDocumentWithViolation2() {
        doc.setPropertyValue(SIMPLE_FIELD, -12345);
        checkNumericIntervalOnGroupCode(validator.validate(doc));
    }

    @Test
    public void testDocumentDirtyWithViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, null);
        checkNotNullOnGroupCode(validator.validate(doc, true));
    }

    @Test
    public void testDocumentNotDirtyWithViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, null);
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        checkOk(validator.validate(doc, true));
    }

    @Test
    public void testFieldWithoutViolation() {
        Field field = metamodel.getField(SIMPLE_FIELD);
        checkOk(validator.validate(field, 12345));
    }

    @Test
    public void testFieldWithViolation1() {
        Field field = metamodel.getField(SIMPLE_FIELD);
        checkNotNullOnGroupCode(validator.validate(field, null));
    }

    @Test
    public void testFieldWithViolation2() {
        Field field = metamodel.getField(SIMPLE_FIELD);
        checkNumericIntervalOnGroupCode(validator.validate(field, -12345));
    }

    @Test
    public void testSchemaFieldWithoutViolation() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(SIMPLE_FIELD);
        checkOk(validator.validate(schema, field, 12345));
    }

    @Test
    public void testSchemaFieldWithViolation1() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(SIMPLE_FIELD);
        checkNotNullOnGroupCode(validator.validate(schema, field, null));
    }

    @Test
    public void testSchemaFieldWithViolation2() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(SIMPLE_FIELD);
        checkNumericIntervalOnGroupCode(validator.validate(schema, field,
                -12345));
    }

    @Test
    public void testSchemaFieldComplexWithoutViolation() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(COMPLEX_FIELD);
        checkOk(validator.validate(schema, field, createUser("Bob", "Sponge")));
    }

    @Test
    public void testSchemaFieldComplexWithViolation1() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(COMPLEX_FIELD);
        checkNotNullOnManagerFirstname(validator.validate(schema, field,
                createUser(null, "Sponge")));
    }

    @Test
    public void testSchemaFieldComplexWithViolation2() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(COMPLEX_FIELD);
        checkPatternOnManagerFirstname(validator.validate(schema, field,
                createUser("   ", "Sponge")));
    }

    @Test
    public void testSchemaFieldListWithoutViolation() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(LIST_FIELD);
        ArrayList<String> roles = createRoles("role1", "role2", "role3");
        checkOk(validator.validate(schema, field, roles));
    }

    @Test
    public void testSchemaFieldListWithViolation1() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(LIST_FIELD);
        ArrayList<String> roles = createRoles("role1", null, "role3");
        checkNotNullOnRoles(validator.validate(schema, field, roles));
    }

    @Test
    public void testSchemaFieldListWithViolation2() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(LIST_FIELD);
        ArrayList<String> roles = createRoles("role1", "role2", "invalid role3");
        checkPatternOnRoles(validator.validate(schema, field, roles));
    }

    @Test
    public void testSchemaFieldComplexListWithoutViolation() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(COMPLEX_LIST_FIELD);
        ArrayList<Map<String, String>> value = new ArrayList<Map<String, String>>();
        value.add(createUser("Bob", "Sponge"));
        value.add(createUser("Patrick", "Star"));
        value.add(createUser("Sandy", "Cheeks"));
        checkOk(validator.validate(schema, field, value));
    }

    @Test
    public void testSchemaFieldComplexListWithViolation1() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(COMPLEX_LIST_FIELD);
        ArrayList<Map<String, String>> value = new ArrayList<Map<String, String>>();
        value.add(createUser("Bob", "Sponge"));
        value.add(createUser("Patrick", "Star"));
        value.add(createUser(null, "Cheeks"));
        checkNotNullOnUsersFirstname(validator.validate(schema, field, value));
    }

    @Test
    public void testSchemaFieldComplexListWithViolation2() {
        Schema schema = metamodel.getSchema(SCHEMA);
        Field field = metamodel.getField(COMPLEX_LIST_FIELD);
        ArrayList<Map<String, String>> value = new ArrayList<Map<String, String>>();
        value.add(createUser("Bob", "Sponge"));
        value.add(createUser("Patrick", "Star"));
        value.add(createUser("   ", "Cheeks"));
        checkPatternOnUsersFirstname(validator.validate(schema, field, value));
    }

    @Test
    public void testSchemaStringFieldStringWithoutViolation() {
        checkOk(validator.validate(SCHEMA, FIELD, 12345));
    }

    @Test
    public void testSchemaStringFieldStringWithViolation1() {
        checkNotNullOnGroupCode(validator.validate(SCHEMA, FIELD, null));
    }

    @Test
    public void testSchemaStringFieldStringWithViolation2() {
        checkNumericIntervalOnGroupCode(validator.validate(SCHEMA, FIELD,
                -12345));
    }

    @Test
    public void testFieldXPathWithoutViolation() {
        checkOk(validator.validate(SCHEMA, FIELD, 12345));
    }

    @Test
    public void testFieldXPathWithViolation1() {
        checkNotNullOnGroupCode(validator.validate(SCHEMA, FIELD, null));
    }

    @Test
    public void testFieldXPathWithViolation2() {
        checkNumericIntervalOnGroupCode(validator.validate(SCHEMA, FIELD,
                -12345));
    }

    @Test
    public void testSchemaMapWithoutViolation() {
        Map<String, Serializable> values = new HashMap<String, Serializable>();
        values.put(FIELD, 12345);
        checkOk(validator.validate(SCHEMA, values));
    }

    @Test
    public void testSchemaMapWithViolation1() {
        Map<String, Serializable> values = new HashMap<String, Serializable>();
        values.put(FIELD, null);
        checkNotNullOnGroupCode(validator.validate(SCHEMA, values));
    }

    @Test
    public void testSchemaMapWithViolation2() {
        Map<String, Serializable> values = new HashMap<String, Serializable>();
        values.put(FIELD, -12345);
        checkNumericIntervalOnGroupCode(validator.validate(SCHEMA, values));
    }

    @Test
    public void testFieldMapWithoutViolation() {
        Map<String, Serializable> values = new HashMap<String, Serializable>();
        values.put(SIMPLE_FIELD, 12345);
        checkOk(validator.validate(values));
    }

    @Test
    public void testFieldMapWithViolation1() {
        Map<String, Serializable> values = new HashMap<String, Serializable>();
        values.put(SIMPLE_FIELD, null);
        checkNotNullOnGroupCode(validator.validate(values));
    }

    @Test
    public void testFieldMapWithViolation2() {
        Map<String, Serializable> values = new HashMap<String, Serializable>();
        values.put(SIMPLE_FIELD, -12345);
        checkNumericIntervalOnGroupCode(validator.validate(values));
    }

    @Test
    public void testComplexFieldWithoutViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        doc.setPropertyValue(COMPLEX_FIELD, createUser("Bob", "Sponge"));
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        checkOk(validator.validate(doc));
    }

    @Test
    public void testComplexFieldWithSubFieldViolation1() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        doc.setPropertyValue(COMPLEX_FIELD, createUser(null, "Sponge"));
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        checkNotNullOnManagerFirstname(validator.validate(doc));
    }

    @Test
    public void testComplexFieldWithSubFieldViolation2() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        doc.setPropertyValue(COMPLEX_FIELD, createUser("   ", "Sponge"));
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        checkPatternOnManagerFirstname(validator.validate(doc));
    }

    @Test
    public void testListFieldWithoutViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<String> value = createRoles("role1", "role2", "role3");
        doc.setPropertyValue(LIST_FIELD, value);
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        checkOk(validator.validate(doc));
    }

    @Test
    public void testListFieldWithSubFieldNullViolation1() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<String> value = createRoles("role1", null, "role3");
        doc.setPropertyValue(LIST_FIELD, value);
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        checkNotNullOnRoles(validator.validate(doc));
    }

    @Test
    public void testListFieldWithSubFieldPatternViolation2() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<String> value = createRoles("role1", "role2", "invalid role3");
        doc.setPropertyValue(LIST_FIELD, value);
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        checkPatternOnRoles(validator.validate(doc));
    }

    @Test
    public void testComplexListFieldWithoutViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<Map<String, String>> value = new ArrayList<Map<String, String>>();
        value.add(createUser("Bob", "Sponge"));
        value.add(createUser("Patrick", "Star"));
        value.add(createUser("Sandy", "Cheeks"));
        doc.setPropertyValue(COMPLEX_LIST_FIELD, value);
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        checkOk(validator.validate(doc));
    }

    @Test
    public void testComplexListFieldWithItemSubFieldNullViolation1() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<Map<String, String>> value = new ArrayList<Map<String, String>>();
        value.add(createUser("Bob", "Sponge"));
        value.add(createUser("Patrick", "Star"));
        value.add(createUser(null, "Cheeks"));
        doc.setPropertyValue(COMPLEX_LIST_FIELD, value);
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        checkNotNullOnUsersFirstname(validator.validate(doc));
    }

    @Test
    public void testComplexListFieldWithSubFieldPatternViolation2() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<Map<String, String>> value = new ArrayList<Map<String, String>>();
        value.add(createUser("Bob", "Sponge"));
        value.add(createUser("Patrick", "Star"));
        value.add(createUser("   ", "Cheeks"));
        doc.setPropertyValue(COMPLEX_LIST_FIELD, value);
        doc = session.createDocument(doc);
        doc = session.saveDocument(doc);
        checkPatternOnUsersFirstname(validator.validate(doc));
    }

    // //////////////////////////////////////
    // End of the tests : Usefull methods //

    private ArrayList<String> createRoles(String... roles) {
        return new ArrayList<String>(Arrays.asList(roles));
    }

    private HashMap<String, String> createUser(String firstname, String lastname) {
        HashMap<String, String> user = new HashMap<String, String>();
        user.put("firstname", firstname);
        user.put("lastname", lastname);
        return user;
    }

    private void checkOk(Set<ConstraintViolation> violations) {
        assertEquals(0, violations.size());
    }

    private void checkNotNullOnGroupCode(Set<ConstraintViolation> violations) {
        assertEquals(1, violations.size());
        ConstraintViolation violation = violations.iterator().next();
        assertEquals(NotNullConstraint.get(), violation.getConstraint());
        assertEquals(SCHEMA, violation.getSchema().getName());
        assertEquals(1, violation.getFieldPath().size());
        String fieldName = violation.getFieldPath().get(0).getName().getPrefixedName();
        assertEquals(SIMPLE_FIELD, fieldName);
        assertNull(violation.getInvalidValue());
    }

    private void checkNumericIntervalOnGroupCode(
            Set<ConstraintViolation> violations) {
        assertEquals(1, violations.size());
        ConstraintViolation violation = violations.iterator().next();
        Constraint constraint = violation.getConstraint();
        assertTrue(constraint instanceof NumericIntervalConstraint);
        assertEquals(SCHEMA, violation.getSchema().getName());
        assertEquals(1, violation.getFieldPath().size());
        String fieldName = violation.getFieldPath().get(0).getName().getPrefixedName();
        assertEquals(SIMPLE_FIELD, fieldName);
        assertEquals(-12345, ((Number) violation.getInvalidValue()).intValue());
    }

    private void checkNotNullOnManagerFirstname(
            Set<ConstraintViolation> violations) {
        assertEquals(1, violations.size());
        ConstraintViolation violation = violations.iterator().next();
        assertEquals(NotNullConstraint.get(), violation.getConstraint());
        assertEquals(SCHEMA, violation.getSchema().getName());
        assertEquals(2, violation.getFieldPath().size());
        String fieldName1 = violation.getFieldPath().get(0).getName().getPrefixedName();
        assertEquals("vs:manager", fieldName1);
        String fieldName2 = violation.getFieldPath().get(1).getName().getPrefixedName();
        assertEquals("firstname", fieldName2);
        assertNull(violation.getInvalidValue());
    }

    private void checkPatternOnManagerFirstname(
            Set<ConstraintViolation> violations) {
        assertEquals(1, violations.size());
        ConstraintViolation violation = violations.iterator().next();
        assertTrue(violation.getConstraint() instanceof PatternConstraint);
        assertEquals(SCHEMA, violation.getSchema().getName());
        assertEquals(2, violation.getFieldPath().size());
        String fieldName1 = violation.getFieldPath().get(0).getName().getPrefixedName();
        assertEquals("vs:manager", fieldName1);
        String fieldName2 = violation.getFieldPath().get(1).getName().getPrefixedName();
        assertEquals("firstname", fieldName2);
        assertEquals("   ", violation.getInvalidValue());
    }

    private void checkNotNullOnRoles(Set<ConstraintViolation> violations) {
        assertEquals(1, violations.size());
        ConstraintViolation violation = violations.iterator().next();
        assertEquals(NotNullConstraint.get(), violation.getConstraint());
        assertEquals(SCHEMA, violation.getSchema().getName());
        assertEquals(2, violation.getFieldPath().size());
        String fieldName1 = violation.getFieldPath().get(0).getName().getPrefixedName();
        assertEquals("vs:roles", fieldName1);
        String fieldName2 = violation.getFieldPath().get(1).getName().getPrefixedName();
        assertEquals("role", fieldName2);
        assertNull(violation.getInvalidValue());
    }

    private void checkPatternOnRoles(Set<ConstraintViolation> violations) {
        assertEquals(1, violations.size());
        ConstraintViolation violation = violations.iterator().next();
        assertTrue(violation.getConstraint() instanceof PatternConstraint);
        assertEquals(SCHEMA, violation.getSchema().getName());
        assertEquals(2, violation.getFieldPath().size());
        String fieldName1 = violation.getFieldPath().get(0).getName().getPrefixedName();
        assertEquals("vs:roles", fieldName1);
        String fieldName2 = violation.getFieldPath().get(1).getName().getPrefixedName();
        assertEquals("role", fieldName2);
        assertEquals("invalid role3", violation.getInvalidValue());
    }

    private void checkNotNullOnUsersFirstname(
            Set<ConstraintViolation> violations) {
        assertEquals(1, violations.size());
        ConstraintViolation violation = violations.iterator().next();
        assertEquals(NotNullConstraint.get(), violation.getConstraint());
        assertEquals(SCHEMA, violation.getSchema().getName());
        assertEquals(3, violation.getFieldPath().size());
        String fieldName1 = violation.getFieldPath().get(0).getName().getPrefixedName();
        assertEquals("vs:users", fieldName1);
        String fieldName2 = violation.getFieldPath().get(1).getName().getPrefixedName();
        assertEquals("user", fieldName2);
        String fieldName3 = violation.getFieldPath().get(2).getName().getPrefixedName();
        assertEquals("firstname", fieldName3);
        assertNull(violation.getInvalidValue());
    }

    private void checkPatternOnUsersFirstname(
            Set<ConstraintViolation> violations) {
        assertEquals(1, violations.size());
        ConstraintViolation violation = violations.iterator().next();
        assertTrue(violation.getConstraint() instanceof PatternConstraint);
        assertEquals(SCHEMA, violation.getSchema().getName());
        assertEquals(3, violation.getFieldPath().size());
        String fieldName1 = violation.getFieldPath().get(0).getName().getPrefixedName();
        assertEquals("vs:users", fieldName1);
        String fieldName2 = violation.getFieldPath().get(1).getName().getPrefixedName();
        assertEquals("user", fieldName2);
        String fieldName3 = violation.getFieldPath().get(2).getName().getPrefixedName();
        assertEquals("firstname", fieldName3);
        assertEquals("   ", violation.getInvalidValue());
    }

}
