/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Chapurlat <nchapurat@nuxeo.com>
 */

package org.nuxeo.ecm.core.schema.types.constraints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.Map;

import org.junit.Test;
import org.nuxeo.ecm.core.schema.types.primitives.BinaryType;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.IntegerType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;

public class TestPatternConstraint {

    static final String PATTERN = "[0-9]{4}";

    static final String YES = "1234";

    static final String NO = "12345";

    PatternConstraint simple = new PatternConstraint(PATTERN);

    @Test
    public void testPatternConstraintSupportedType() {
        assertTrue(DoubleType.INSTANCE.support(PatternConstraint.class));
        assertTrue(IntegerType.INSTANCE.support(PatternConstraint.class));
        assertTrue(LongType.INSTANCE.support(PatternConstraint.class));
        assertTrue(StringType.INSTANCE.support(PatternConstraint.class));
        assertFalse(DateType.INSTANCE.support(PatternConstraint.class));
        assertFalse(BinaryType.INSTANCE.support(PatternConstraint.class));
        assertFalse(BooleanType.INSTANCE.support(PatternConstraint.class));
    }

    @Test
    public void testPatternConstraintNominals() {
        assertTrue(simple.validate(YES));
        assertFalse(simple.validate(NO));
    }

    @Test
    public void testPatternConstraintNullIsOk() {
        assertTrue(simple.validate(null));
    }

    @Test
    public void testPatternConstraintHandleString() {
        assertTrue(simple.validate(YES));
        assertFalse(simple.validate(NO));
    }

    @Test
    public void testPatternConstraintHandleAnyObject() {
        assertTrue(simple.validate(new Object() {
            @Override
            public String toString() {
                return "1234";
            }
        }));
        assertFalse(simple.validate(new Object() {
            @Override
            public String toString() {
                return "12345";
            }
        }));
    }

    @Test
    public void testPatternConstraintDescription() {
        Constraint.Description description = simple.getDescription();
        assertEquals("PatternConstraint", description.getName());
        Map<String, Serializable> params = description.getParameters();
        assertEquals(1, params.size());
        assertTrue(params.containsKey("Pattern"));
        assertEquals(PATTERN, params.get("Pattern"));
    }

}
