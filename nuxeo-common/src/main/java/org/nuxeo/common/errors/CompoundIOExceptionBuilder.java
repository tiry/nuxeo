package org.nuxeo.common.errors;

import java.io.IOException;
import java.util.List;

public class CompoundIOExceptionBuilder extends
        CompoundExceptionBuilder<IOException> {

    @Override
    protected IOException newThrowable(List<IOException> causes) {
        IOException error = new IOException("compound io exception");
        for (Throwable cause:causes) {
            error.addSuppressed(cause);
        }
        return error;
    }

}