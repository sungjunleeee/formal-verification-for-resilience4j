package org.slf4j;

public class LoggerFactory {
    public static Logger getLogger(Class<?> clazz) {
        return new Logger() {
            @Override
            public void debug(String msg) {
            }

            @Override
            public void debug(String format, Object arg) {
            }

            @Override
            public void debug(String format, Object arg1, Object arg2) {
            }

            @Override
            public void debug(String format, Object... arguments) {
            }

            @Override
            public void info(String msg) {
            }

            @Override
            public void info(String format, Object arg) {
            }

            @Override
            public void info(String format, Object arg1, Object arg2) {
            }

            @Override
            public void info(String format, Object... arguments) {
            }

            @Override
            public void warn(String msg) {
            }

            @Override
            public void warn(String format, Object arg) {
            }

            @Override
            public void warn(String format, Object... arguments) {
            }

            @Override
            public void warn(String msg, Throwable t) {
            }

            @Override
            public void error(String msg) {
            }

            @Override
            public void error(String format, Object arg) {
            }

            @Override
            public void error(String format, Object... arguments) {
            }

            @Override
            public void error(String msg, Throwable t) {
            }

            @Override
            public boolean isDebugEnabled() {
                return false;
            }

            @Override
            public boolean isInfoEnabled() {
                return false;
            }

            @Override
            public boolean isWarnEnabled() {
                return false;
            }

            @Override
            public boolean isErrorEnabled() {
                return false;
            }
        };
    }
}
