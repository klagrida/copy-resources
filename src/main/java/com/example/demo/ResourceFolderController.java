package com.example.demo;

import com.example.demo.ResourceFolderCopier.CopyResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;

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
    private final Path destRoot;

    public ResourceFolderController(ResourceFolderCopier copier,
                                    @Value("${app.resource-copy.dest-dir}") String destDir) {
        this.copier = copier;
        this.destRoot = Path.of(destDir);
    }

    /**
     * Copies a classpath folder to disk. The destination is taken from configuration
     * ({@code app.resource-copy.dest-dir}) with the folder name appended; if it already
     * exists it is backed up first.
     *
     * <pre>{@code
     * PUT /api/resource-folders/templates
     * }</pre>
     *
     * @param baseFolder folder name under resources (path variable), e.g. "templates"
     * @return a summary of what was copied and where the previous copy was backed up
     */
    @PutMapping("/{baseFolder}")
    public ResponseEntity<CopyResponse> copy(@PathVariable String baseFolder) {
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
     *
     * @param backup path the previous destination was moved to, or {@code null} if none
     */
    public record CopyResponse(String baseFolder, String destination, int filesCopied,
                               String backup) {
    }
}
