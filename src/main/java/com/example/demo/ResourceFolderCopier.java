package com.example.demo;

import com.example.demo.ResourceFolderLister.Entry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Copies a resource folder (and all of its subfolders) out of the classpath to a
 * destination directory on disk, preserving the original folder structure.
 *
 * <p>Enumeration is delegated to {@link ResourceFolderLister}, which returns directories
 * and files in parent-first order. Files are read through the classpath (never as
 * {@code File}s), so this works identically in the IDE and inside a packaged JAR/WAR, on
 * any OS.
 */
@Service
public class ResourceFolderCopier {

    private static final DateTimeFormatter BACKUP_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ResourceFolderLister lister;

    public ResourceFolderCopier(ResourceFolderLister lister) {
        this.lister = lister;
    }

    /**
     * Copies {@code baseFolder} from the classpath into {@code destination}.
     *
     * <p>If {@code destination} already exists, it is first renamed aside to a timestamped
     * backup sibling (e.g. {@code templates.backup-20260601-233000}) so the previous
     * contents are preserved, and the files are then copied into a fresh destination.
     *
     * @param baseFolder  folder name under {@code src/main/resources}, e.g. "templates"
     * @param destination target directory on disk, e.g. {@code /opt/app/templates}
     * @return a summary of the operation, including the backup path if one was made
     */
    public CopyResult copyFolder(String baseFolder, Path destination) throws IOException {
        // If the destination already exists, move it aside before writing the new tree.
        Path backup = null;
        if (Files.exists(destination)) {
            backup = backupPath(destination);
            Files.move(destination, backup);
        }
        Files.createDirectories(destination);

        List<Entry> entries = lister.listEntries(baseFolder);

        int copied = 0;
        for (Entry entry : entries) {
            // Resolve segment-by-segment so the "/"-separated path maps onto the local
            // filesystem correctly (works on Windows too).
            Path target = destination;
            for (String segment : entry.path().split("/")) {
                if (!segment.isEmpty()) {
                    target = target.resolve(segment);
                }
            }

            if (entry.directory()) {
                Files.createDirectories(target);
            } else {
                try (InputStream is = lister.openStream(baseFolder, entry.path())) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        }
        return new CopyResult(copied, backup);
    }

    /**
     * Builds a non-existing backup path next to {@code destination}, of the form
     * {@code <name>.backup-<timestamp>}, adding a numeric suffix if that already exists.
     */
    private Path backupPath(Path destination) {
        String name = destination.getFileName().toString();
        String stamp = LocalDateTime.now().format(BACKUP_STAMP);
        Path candidate = destination.resolveSibling(name + ".backup-" + stamp);
        int n = 1;
        while (Files.exists(candidate)) {
            candidate = destination.resolveSibling(name + ".backup-" + stamp + "-" + n++);
        }
        return candidate;
    }

    /**
     * Result of a copy operation.
     */
    public static final class CopyResult {
        private final int filesCopied;
        private final Path backup;

        public CopyResult(int filesCopied, Path backup) {
            this.filesCopied = filesCopied;
            this.backup = backup;
        }

        public int filesCopied() {
            return filesCopied;
        }

        public Path backup() {
            return backup;
        }
    }
}
