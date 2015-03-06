package org.nuxeo.osgi.bootstrap;

import java.util.LinkedList;
import java.util.List;

import org.osgi.framework.BundleException;

public class OSGiMultiExceptionHandler {
    final List<Throwable> accumulator = new LinkedList<>();

    final String message;

    public OSGiMultiExceptionHandler(String message) {
        this.message = message;
    }

    public void add(Throwable e) {
        accumulator.add(e);
    }

    public int size() {
        return accumulator.size();
    }

    public void ifExceptionThrow() throws BundleException {
        switch (accumulator.size()) {
            case 0:
                break;
            case 1:
                Throwable th = accumulator.get(0);
                if (th instanceof Error) {
                    throw (Error) th;
                }
                if (th instanceof BundleException) {
                    throw (BundleException) th;
                }
            default:
                BundleException exception = new BundleException(message);
                for (Throwable suppressed : accumulator) {
                    exception.addSuppressed(suppressed);
                }
                throw exception;
        }
    }

    public void ifExceptionThrowRuntime() throws Error {
        switch (accumulator.size()) {
            case 0:
                break;
            case 1:
                Throwable th = accumulator.get(0);
                if (th instanceof Error) {
                    throw (Error) th;
                } else if (th instanceof RuntimeException) {
                    throw (RuntimeException) th;
                } else {
                    throw new RuntimeException(th);
                }
            default:
                RuntimeException exception = new RuntimeException(message);
                for (Throwable suppressed : accumulator) {
                    exception.addSuppressed(suppressed);
                }
                throw exception;
        }
    }

}
