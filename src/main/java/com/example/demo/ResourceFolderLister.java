package com.example.demo;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

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
     * Lists every directory and file under {@code baseFolder}, ordered so that a directory
     * always appears <em>before</em> anything it contains. Recreate the tree on the target
     * system by iterating this list in order: make a directory when {@link Entry#directory()}
     * is {@code true}, write a file otherwise.
     *
     * <p>Directories are derived from the file paths (not from archive directory entries,
     * which are not always present), so the result is identical in the IDE and inside a
     * packaged JAR/WAR, on any OS.
     *
     * @param baseFolder folder name under {@code src/main/resources}, e.g. "templates"
     * @return ordered directory + file entries, each path prefixed with the base folder
     */
    public List<Entry> listEntries(String baseFolder) throws IOException {
        Resource[] resources =
                resolver.getResources("classpath*:" + baseFolder + "/**/*");

        TreeSet<String> directories = new TreeSet<>();
        TreeSet<String> files = new TreeSet<>();
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue; // skip archive directory entries; we derive directories ourselves
            }
            String path = baseFolder + "/" + relativePath(resource, baseFolder);
            files.add(path);

            // record every ancestor directory of this file, including the base folder
            for (int slash = path.lastIndexOf('/'); slash > 0; slash = path.lastIndexOf('/', slash - 1)) {
                directories.add(path.substring(0, slash));
            }
        }

        List<Entry> entries = new ArrayList<>(directories.size() + files.size());
        directories.forEach(d -> entries.add(new Entry(d, true)));
        files.forEach(f -> entries.add(new Entry(f, false)));
        // lexicographic order on the path puts each directory before everything inside it
        entries.sort(Comparator.comparing(Entry::path));
        return entries;
    }

    /**
     * Opens the content of a listed file for reading. Pass a base-folder-prefixed path as
     * produced by {@link #listFiles} / {@link #listEntries}, e.g. {@code "templates/sub/b.txt"}.
     * Reads via the classpath, so it works in the IDE and inside a packaged JAR/WAR.
     *
     * <p>The caller is responsible for closing the returned stream.
     */
    public InputStream openStream(String path) throws IOException {
        return resolver.getResource("classpath:" + path).getInputStream();
    }

    /**
     * A single entry in the listing.
     *
     * @param path      base-folder-prefixed path with {@code '/'} separators, e.g.
     *                  {@code "templates/sub"} or {@code "templates/sub/b.txt"}
     * @param directory {@code true} for a directory, {@code false} for a file
     */
    public record Entry(String path, boolean directory) {
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
