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
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.runtime.services.event;

import java.util.Arrays;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.RuntimeServiceException;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@XObject("listener")
public class ListenerDescriptor {

    @XNodeList(value = "topic", type = String[].class, componentType = String.class)
    String[] topics;

    EventListener listener;

    @XNode("@class")
    public void setListener(Class<EventListener> listenerClass) {
        try {
            listener = listenerClass.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeServiceException("Cannot load " + listenerClass, e);
        }
    }

    @Override
    public String toString() {
        return listener + " { " + Arrays.toString(topics) + " }";
    }

}

class NullListener implements EventListener {

    @Override
    public boolean aboutToHandleEvent(Event event) {
        return false;
    }

    @Override
    public void handleEvent(Event event) {
    }

}
