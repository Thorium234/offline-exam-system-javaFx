package com.zaraki.exams.util;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

public class LoggerUtil {

    private static final Logger LOGGER = Logger.getLogger("ExamSystem");
    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        LOGGER.setUseParentHandlers(false);

        ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(new SimpleFormatter());
        ch.setLevel(Level.INFO);
        LOGGER.addHandler(ch);

        try {
            String logPath = Paths.get(System.getProperty("user.dir"), "exam_system.log").toString();
            FileHandler fh = new FileHandler(logPath, true);
            fh.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord r) {
                    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        + " [" + r.getLevel() + "] " + r.getSourceClassName() + " - "
                        + r.getMessage() + "\n";
                }
            });
            fh.setLevel(Level.ALL);
            LOGGER.addHandler(fh);
        } catch (IOException e) {
            System.err.println("Failed to create log file: " + e.getMessage());
        }

        initialized = true;
    }

    public static Logger getLogger() {
        if (!initialized) init();
        return LOGGER;
    }

    public static void info(String msg) { getLogger().info(msg); }
    public static void warn(String msg) { getLogger().warning(msg); }
    public static void severe(String msg) { getLogger().severe(msg); }
    public static void fine(String msg) { getLogger().fine(msg); }
}
