package pers.liuqi.rpc.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mayer - [Created on 2015-10-27]
 */
public final class RuntimeLogger {

    private static Logger logger = LoggerFactory.getLogger(RuntimeLogger.class);

    public static void trace(final Object... objects) {
        logger.trace(StringUtils.join(objects, '|'));
    }

    public static void debug(final Object... objects) {
        logger.debug(StringUtils.join(objects, '|'));
    }

    public static void info(final Object... objects) {
        logger.info(StringUtils.join(objects, '|'));
    }

    public static void warn(final Object... objects) {
        logger.warn(StringUtils.join(objects, '|'));
    }

    public static void error(final Object... objects) {
        logger.error(StringUtils.join(objects, '|'));
    }

}
