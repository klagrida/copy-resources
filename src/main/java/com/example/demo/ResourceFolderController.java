package com.example.demo;

import com.example.demo.ResourceFolderCopier.CopyResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

/**
 * Exposes {@link ResourceFolderCopier} over HTTP.
 *
 * <p>The copy is modelled as a PUT because it is idempotent: applying the same request
 * repeatedly produces the same destination tree (the previous one is backed up first).
 */
@RestController
@RequestMapping("/api/resource-folders")
public class ResourceFolderController {

    private final ResourceFolderCopier copier;
    private final String baseFolder;
    private final Path destRoot;

    public ResourceFolderController(ResourceFolderCopier copier,
                                    @Value("${app.resource-copy.base-folder}") String baseFolder,
                                    @Value("${app.resource-copy.dest-dir}") String destDir) {
        this.copier = copier;
        this.baseFolder = baseFolder;
        this.destRoot = Paths.get(destDir);
    }

    /**
     * Copies the configured classpath folder ({@code app.resource-copy.base-folder}) to the
     * configured destination ({@code app.resource-copy.dest-dir}/&lt;folder&gt;); if the
     * destination already exists it is backed up first.
     *
     * <pre>{@code
     * PUT /api/resource-folders
     * }</pre>
     *
     * @return a summary of what was copied and where the previous copy was backed up
     */
    @PutMapping
    public ResponseEntity<CopyResponse> copy() {
        Path destination = destRoot.resolve(baseFolder);
        try {
            CopyResult result = copier.copyFolder(baseFolder, destination);
            String backup = result.backup() != null ? result.backup().toString() : null;
            return ResponseEntity.ok(new CopyResponse(
                    baseFolder, destination.toString(), result.filesCopied(), backup));
        } catch (IOException e) {
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "Failed to copy folder '" + baseFolder + "': " + e.getMessage(), e);
        }
    }

    /**
     * Response summarising a completed copy operation.
     */
    public static final class CopyResponse {
        private final String baseFolder;
        private final String destination;
        private final int filesCopied;
        private final String backup;

        public CopyResponse(String baseFolder, String destination, int filesCopied, String backup) {
            this.baseFolder = baseFolder;
            this.destination = destination;
            this.filesCopied = filesCopied;
            this.backup = backup;
        }

        public String getBaseFolder() {
            return baseFolder;
        }

        public String getDestination() {
            return destination;
        }

        public int getFilesCopied() {
            return filesCopied;
        }

        public String getBackup() {
            return backup;
        }
    }
}
