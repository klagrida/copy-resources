package com.example.demo;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lists the files bundled under a resource folder, returning each file's path
 * <em>relative to that folder</em> (e.g. {@code "a.txt"}, {@code "sub/b.txt"},
 * {@code "sub/deep/c.json"}).
 *
 * <p>Works in the IDE and inside a packaged JAR/WAR, on any OS, because resources are
 * located through the classpath rather than the filesystem. Paths always use forward
 * slashes and are URL-decoded, so they are ready to be recreated on another system.
 */
@Service
public class ResourceFolderLister {

    private final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();

    /**
     * @param baseFolder folder name under {@code src/main/resources}, e.g. "templates"
     * @return the paths of every file under {@code baseFolder}, sorted; each path is
     *         prefixed with the base folder (e.g. {@code "templates/sub/b.txt"}) and uses
     *         {@code '/'} as separator
     */
    public List<String> listFiles(String baseFolder) throws IOException {
        // "**/*" = every file in every subfolder, recursively.
        // classpath*: scans all matching locations, including jars/wars.
        Resource[] resources =
                resolver.getResources("classpath*:" + baseFolder + "/**/*");

        List<String> paths = new ArrayList<>();
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue; // skip directory entries
            }
            paths.add(baseFolder + "/" + relativePath(resource, baseFolder));
        }
        Collections.sort(paths);
        return paths;
    }

    /**
     * Extracts the path relative to the base folder, using forward slashes and decoding
     * URL percent-escapes. e.g. {@code "jar:file:/app.jar!/templates/sub/a.txt"} -> {@code "sub/a.txt"}.
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
}
