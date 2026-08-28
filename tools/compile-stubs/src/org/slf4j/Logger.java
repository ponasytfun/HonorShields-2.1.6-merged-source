package org.slf4j;

public interface Logger {
    void info(String message);
    void info(String message, Object... arguments);
    void warn(String message, Object... arguments);
    void error(String message, Object first, Object second);
    void error(String message, Object... arguments);
}
