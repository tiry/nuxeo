package org.nuxeo.runtime.test.mockito;

import javax.inject.Inject;

import org.junit.runner.RunWith;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@Features(MockitoFeature.class)
@RunWith(FeaturesRunner.class)
public abstract class MockitoTestCase {

    @Inject
    protected MockitoFeature mockito;

    protected <T> T mock(Class<T> type) {
        if (javax.servlet.http.HttpServletRequest.class.equals(type)) {
            return type.cast(mockito.mock(org.nuxeo.runtime.test.mockito.http.HttpServletRequest.class));
        }
        if (javax.servlet.http.HttpServletResponse.class.equals(type)) {
            return type.cast(mockito.mock(org.nuxeo.runtime.test.mockito.http.HttpServletResponse.class));
        }
        return mockito.mock(type);
    }
}
