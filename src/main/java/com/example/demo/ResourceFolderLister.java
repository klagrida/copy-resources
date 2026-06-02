package com.example.demo;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Lists the files bundled under a resource folder, returning each file's path
 * relative to that folder (e.g. {@code "a.txt"}, {@code "sub/b.txt"},
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
     *         relative to the base folder (e.g. {@code "sub/b.txt"}) and uses
     *         {@code '/'} as separator
     */
    public List<String> listFiles(String baseFolder) throws IOException {
        Resource[] resources =
                resolver.getResources("classpath*:" + baseFolder + "/**/*");

        List<String> paths = new ArrayList<String>();
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }
            paths.add(relativePath(resource, baseFolder));
        }
        Collections.sort(paths);
        return paths;
    }

    /**
     * Lists every directory and file under {@code baseFolder}, ordered so that a directory
     * always appears before anything it contains. Paths are relative to the base folder
     * (e.g. {@code "sub"}, {@code "sub/b.txt"}).
     *
     * @param baseFolder folder name under {@code src/main/resources}, e.g. "templates"
     * @return ordered directory + file entries with paths relative to the base folder
     */
    public List<Entry> listEntries(String baseFolder) throws IOException {
        Resource[] resources =
                resolver.getResources("classpath*:" + baseFolder + "/**/*");

        TreeSet<String> directories = new TreeSet<String>();
        TreeSet<String> files = new TreeSet<String>();
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }
            String path = relativePath(resource, baseFolder);
            files.add(path);

            // record every ancestor directory of this file
            for (int slash = path.lastIndexOf('/'); slash > 0; slash = path.lastIndexOf('/', slash - 1)) {
                directories.add(path.substring(0, slash));
            }
        }

        List<Entry> entries = new ArrayList<Entry>(directories.size() + files.size());
        for (String d : directories) {
            entries.add(new Entry(d, true));
        }
        for (String f : files) {
            entries.add(new Entry(f, false));
        }
        Collections.sort(entries, Comparator.comparing(Entry::path));
        return entries;
    }

    /**
     * Opens the content of a listed file for reading. Pass the base folder and the
     * relative path as produced by {@link #listFiles} / {@link #listEntries},
     * e.g. {@code openStream("templates", "sub/b.txt")}.
     *
     * <p>The caller is responsible for closing the returned stream.
     */
    public InputStream openStream(String baseFolder, String relativePath) throws IOException {
        return resolver.getResource("classpath:" + baseFolder + "/" + relativePath).getInputStream();
    }

    /**
     * A single entry in the listing.
     */
    public static final class Entry {
        private final String path;
        private final boolean directory;

        public Entry(String path, boolean directory) {
            this.path = path;
            this.directory = directory;
        }

        public String path() {
            return path;
        }

        public boolean directory() {
            return directory;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry)) return false;
            Entry other = (Entry) o;
            return directory == other.directory && path.equals(other.path);
        }

        @Override
        public int hashCode() {
            return 31 * path.hashCode() + (directory ? 1 : 0);
        }

        @Override
        public String toString() {
            return "Entry{path='" + path + "', directory=" + directory + '}';
        }
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
            return resource.getFilename();
        }
        return decode(url.substring(idx + marker.length()));
    }

    /**
     * Decodes percent-escapes from a URL path while preserving a literal {@code '+'}.
     */
    private static String decode(String urlPath) throws UnsupportedEncodingException {
        return URLDecoder.decode(urlPath.replace("+", "%2B"), "UTF-8");
    }
}
