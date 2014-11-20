/*
 * Copyright (c) 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */

package org.nuxeo.ecm.core.schema.types.constraints;

import java.util.Calendar;
import java.util.Date;

/**
 * <p>
 * This constraint ensures some date representation is in an enumeration.
 * </p>
 * <p>
 * This constraint can validate any {@link Date} or {@link Calendar}. This
 * constraint also support {@link Number} types whose long value is recognised
 * as number of milliseconds since January 1, 1970, 00:00:00 GMT.
 * </p>
 * 
 * @since 7.1
 */
public abstract class AbstractConstraint implements Constraint {

    private static final long serialVersionUID = 1L;

    @Override
    public final String toString() {
        return getDescription().toString();
    }

}
