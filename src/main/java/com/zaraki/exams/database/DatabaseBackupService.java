package com.zaraki.exams.database;

import com.zaraki.exams.util.LoggerUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

public class DatabaseBackupService {

    private static final Logger LOG = LoggerUtil.getLogger();
    private static final String BACKUP_DIR = "backups";

    private DatabaseBackupService() {}

    public static Path createBackup(String dbPath) {
        try {
            Path backupDir = Paths.get(System.getProperty("user.dir"), BACKUP_DIR);
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupName = "exam_system_backup_" + timestamp + ".db";
            Path backupFile = backupDir.resolve(backupName);

            Files.copy(Paths.get(dbPath), backupFile, StandardCopyOption.REPLACE_EXISTING);

            LOG.info("Database backup created: " + backupFile.toAbsolutePath());
            return backupFile;
        } catch (IOException e) {
            LOG.severe("Failed to create database backup: " + e.getMessage());
            return null;
        }
    }

    public static boolean restoreBackup(String backupFilePath, String dbPath) {
        try {
            Path source = Paths.get(backupFilePath);
            if (!Files.exists(source)) {
                LOG.severe("Backup file not found: " + backupFilePath);
                return false;
            }

            Path dbFile = Paths.get(dbPath);
            Files.copy(source, dbFile, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Database restored from backup: " + backupFilePath);
            return true;
        } catch (IOException e) {
            LOG.severe("Failed to restore database backup: " + e.getMessage());
            return false;
        }
    }

    public static void cleanupOldBackups(int keepCount) {
        try {
            Path backupDir = Paths.get(System.getProperty("user.dir"), BACKUP_DIR);
            if (!Files.exists(backupDir)) return;

            Files.list(backupDir)
                .filter(p -> p.toString().endsWith(".db"))
                .sorted((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .skip(keepCount)
                .forEach(p -> {
                    try {
                        Files.delete(p);
                        LOG.info("Deleted old backup: " + p.getFileName());
                    } catch (IOException e) {
                        LOG.warning("Failed to delete old backup: " + p.getFileName());
                    }
                });
        } catch (IOException e) {
            LOG.warning("Failed to cleanup old backups: " + e.getMessage());
        }
    }
}
