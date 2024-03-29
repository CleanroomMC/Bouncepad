package net.minecraft.launchwrapper;

import com.cleanroommc.bouncepad.Bouncepad;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Deprecated(since = "0.5")
public class LogWrapper {

    public static LogWrapper log = new LogWrapper();

    private Logger myLog;

    private static void configureLogging() {
        if (log.myLog == null) {
            log.myLog = Bouncepad.logger();
        }
    }

    public static void retarget(Logger to) {
        if (to != null) {
            log.myLog = to;
        } else {
            log.myLog = Bouncepad.logger();
        }
    }

    public static void log(String logChannel, Level level, String format, Object... data) {
        Logger logger = makeAndGetLog(logChannel);
        if (format.contains("{}")) {
            logger.log(level, format, data);
        } else {
            logger.log(level, String.format(format, data));
        }
    }

    public static void log(Level level, String format, Object... data) {
        configureLogging();
        if (format.contains("{}")) {
            log.myLog.log(level, format, data);
        } else {
            log.myLog.log(level, String.format(format, data));
        }
    }

    public static void log(String logChannel, Level level, Throwable ex, String format, Object... data) {
        Logger logger = makeAndGetLog(logChannel);
        if (format.contains("{}")) {
            logger.log(level, format, data, ex);
        } else {
            logger.log(level, String.format(format, data), ex);
        }
    }

    public static void log(Level level, Throwable ex, String format, Object... data) {
        configureLogging();
        if (format.contains("{}")) {
            log.myLog.log(level, format, data);
        } else {
            log.myLog.log(level, String.format(format, data));
        }
    }

    public static void severe(String format, Object... data) {
        log(Level.ERROR, format, data);
    }

    public static void warning(String format, Object... data) {
        log(Level.WARN, format, data);
    }

    public static void info(String format, Object... data) {
        log(Level.INFO, format, data);
    }

    public static void fine(String format, Object... data) {
        log(Level.DEBUG, format, data);
    }

    public static void finer(String format, Object... data) {
        log(Level.TRACE, format, data);
    }

    public static void finest(String format, Object... data) {
        log(Level.TRACE, format, data);
    }

    public static void makeLog(String logChannel) {
        LogManager.getLogger(logChannel);
    }

    private static Logger makeAndGetLog(String logChannel) {
        return LogManager.getLogger(logChannel);
    }

}
