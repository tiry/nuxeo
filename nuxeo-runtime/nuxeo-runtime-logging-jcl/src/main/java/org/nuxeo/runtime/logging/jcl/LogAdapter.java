package org.nuxeo.runtime.logging.jcl;

import org.apache.commons.logging.Log;
import org.slf4j.Logger;

public class LogAdapter implements Log {

    final protected String name;

    LogAdapter(String name) {
        this.name = name;
    }

    Logger logger() {
        return LogAdapterActivator.factory.getLogger(name);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger().isDebugEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return logger().isErrorEnabled();
    }

    @Override
    public boolean isFatalEnabled() {
        return logger().isErrorEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return logger().isInfoEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger().isTraceEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return logger().isWarnEnabled();
    }

    @Override
    public void trace(Object message) {
        logger().trace(String.valueOf(message));
    }

    @Override
    public void trace(Object message, Throwable t) {
        logger().trace(String.valueOf(message), t);
    }

    @Override
    public void debug(Object message) {
        logger().debug(String.valueOf(message));
    }

    @Override
    public void debug(Object message, Throwable t) {
        logger().debug(String.valueOf(message), t);
    }

    @Override
    public void info(Object message) {
        logger().info(String.valueOf(message));
    }

    @Override
    public void info(Object message, Throwable t) {
        logger().info(String.valueOf(message), t);
    }

    @Override
    public void warn(Object message) {
        logger().warn(String.valueOf(message));
    }

    @Override
    public void warn(Object message, Throwable t) {
        logger().warn(String.valueOf(message), t);
    }

    @Override
    public void error(Object message) {
        logger().error(String.valueOf(message));
    }

    @Override
    public void error(Object message, Throwable t) {
        logger().error(String.valueOf(message), t);
    }

    @Override
    public void fatal(Object message) {
        logger().error(String.valueOf(message));
    }

    @Override
    public void fatal(Object message, Throwable t) {
        logger().error(String.valueOf(message), t);
    }

}