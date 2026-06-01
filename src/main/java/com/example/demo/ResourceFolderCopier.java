package com.example.demo;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Copies a resource folder (and all of its subfolders) out of the classpath to a
 * destination directory on disk, preserving the original folder structure.
 *
 * <p>Works identically when running from the IDE/exploded classes and from inside a
 * packaged JAR/WAR, because everything is read through {@link Resource#getInputStream()}
 * rather than {@link Resource#getFile()}.
 */
@Service
public class ResourceFolderCopier {

    private final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();

    private static final DateTimeFormatter BACKUP_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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

        // "**/*" = every file in every subfolder, recursively.
        // classpath*: scans all matching locations, including jars/wars.
        Resource[] resources =
                resolver.getResources("classpath*:" + baseFolder + "/**/*");

        int copied = 0;
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue; // skip directory entries
            }

            // Resolve segment-by-segment so the "/"-separated classpath path maps
            // correctly onto the local filesystem (works on Windows too).
            Path target = destination;
            for (String segment : relativePath(resource, baseFolder).split("/")) {
                if (!segment.isEmpty()) {
                    target = target.resolve(segment);
                }
            }

            // recreate the subfolder structure (getParent() is null for a root-level file)
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // process this one file
            try (InputStream is = resource.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                // ... or do whatever per-file work you need here
            }
            copied++;
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
     * Extracts the path relative to the base folder, using forward slashes.
     * e.g. {@code "jar:file:/app.jar!/templates/sub/a.txt"} -> {@code "sub/a.txt"}.
     *
     * <p>The URL form is percent-encoded (e.g. spaces as {@code %20}), so the result is
     * decoded back to real names before it is mapped onto the filesystem.
     */
    private String relativePath(Resource resource, String baseFolder) throws IOException {
        String url = resource.getURL().toString();
        String marker = "/" + baseFolder + "/";
        int idx = url.lastIndexOf(marker);
        if (idx < 0) {
            // base folder is the root of the classpath, fall back to the (decoded) filename
            return resource.getFilename();
        }
        return decode(url.substring(idx + marker.length()));
    }

    /**
     * Decodes percent-escapes from a URL path while preserving a literal {@code '+'}
     * (which {@link URLDecoder} would otherwise turn into a space).
     */
    private static String decode(String urlPath) {
        return URLDecoder.decode(urlPath.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    /**
     * Result of a copy operation.
     *
     * @param filesCopied number of files written into the destination
     * @param backup      where the previous destination was moved, or {@code null} if the
     *                    destination did not previously exist
     */
    public record CopyResult(int filesCopied, Path backup) {
    }
}
