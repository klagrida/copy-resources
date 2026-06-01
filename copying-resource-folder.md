# Copying a Resource Folder (with Subfolders) in a Packaged Spring Boot WAR

## Problem

A Spring Boot application is deployed as a **WAR**. It needs to read files bundled
under `src/main/resources/` — specifically a **folder containing many subfolders and
files** — and process them one by one, optionally copying them to a destination
directory **while preserving the original folder structure**.

The naive approaches break once the app is packaged:

- `ResourceUtils.getFile()` / `new FileInputStream(...)` work in the IDE but **throw
  inside a WAR/JAR**, because the resources no longer exist as discrete files on disk —
  they are entries inside a zip archive.
- `File.listFiles()` cannot iterate a classpath "directory" inside an archive, since
  there is no real folder to list.
- `resource.getFilename()` returns only the **leaf name** (`a.txt`), not the relative
  path (`sub/a.txt`), so the source tree cannot be rebuilt from it alone.

## Solution

Read everything through the **classpath** using `getInputStream()` (never `getFile()`),
and use `PathMatchingResourcePatternResolver` to expand a recursive wildcard pattern.
This works identically in the IDE and inside a deployed WAR.

To preserve structure, compute each file's **relative path** by stripping everything up
to the base folder from the resource URL.

```java
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class ResourceFolderCopier {

    private final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();

    /**
     * @param baseFolder  folder name under resources, e.g. "templates"
     * @param destination target directory on disk, e.g. "/opt/app/templates"
     */
    public void copyFolder(String baseFolder, Path destination) throws IOException {
        // "**/*" = every file in every subfolder, recursively
        Resource[] resources =
                resolver.getResources("classpath*:" + baseFolder + "/**/*");

        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue; // skip directory entries
            }

            String relativePath = relativePath(resource, baseFolder);
            Path target = destination.resolve(relativePath);

            // recreate the subfolder structure
            Files.createDirectories(target.getParent());

            // process this one file
            try (InputStream is = resource.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                // ... or do whatever per-file work you need here
            }
        }
    }

    /**
     * Extracts the path relative to the base folder.
     * e.g. ".../classes/templates/sub/a.txt"  ->  "sub/a.txt"
     */
    private String relativePath(Resource resource, String baseFolder) throws IOException {
        String url = resource.getURL().toString();
        String marker = "/" + baseFolder + "/";
        int idx = url.lastIndexOf(marker);
        if (idx < 0) {
            // base folder is the root of the classpath, fall back to filename
            return resource.getFilename();
        }
        return url.substring(idx + marker.length());
    }
}
```

### Result

Given `src/main/resources/templates/`:

```
resources/templates/
├── a.txt              ->  /opt/app/templates/a.txt
├── sub/
│   ├── b.txt          ->  /opt/app/templates/sub/b.txt
│   └── deep/c.json    ->  /opt/app/templates/sub/deep/c.json
```

The destination tree matches the source tree exactly.

### Why it works

| Concern | Mechanism |
|---|---|
| Works in IDE **and** packaged WAR | Reads via `getInputStream()`, never `getFile()` |
| Recursively finds all files | Pattern `classpath*:<base>/**/*` (`**` recurses, `*` = one level) |
| Scans inside archives | `classpath*:` prefix (the `*` scans **all** matching locations, including jars/wars) |
| Skips directory entries | `resource.isReadable()` guard |
| Preserves subfolder structure | Relative path from URL + `Files.createDirectories(target.getParent())` |

In the IDE the URL is `file:/.../target/classes/templates/sub/b.txt`; inside a WAR it is
`jar:file:/.../app.war!/WEB-INF/classes/templates/sub/b.txt`. Taking everything after the
last `/templates/` yields the correct relative path in both cases.

### Custom per-file processing

For logic beyond a straight copy (parsing, transforming, etc.), replace the
`Files.copy(...)` line while keeping the `try (InputStream is ...)` block — the
`InputStream` and the correct `target` path are already available at that point.

## Limitations & Caveats

- **Build-time only.** This scans resources present on the classpath **at build time**.
  Files dropped into the folder *after* deployment are invisible to it. For runtime-added
  files, point at an external directory instead:
  ```java
  Resource[] resources = resolver.getResources("file:/opt/app/data/**/*");
  ```

- **Cannot write back into the archive.** A packaged WAR is read-only. The destination
  must be an external directory on the filesystem; you can never copy *into* the WAR.

- **Use `classpath*:`, not `classpath:`** for wildcard/directory scans. Plain `classpath:`
  returns only the first match and is unreliable for iterating a folder.

- **Folder-name collision in the path.** `relativePath` uses `lastIndexOf` of
  `/<baseFolder>/`. Because the resource folder is normally the *last* occurrence in the
  URL, this resolves correctly even if the same name appears earlier in the absolute path
  (e.g. `/srv/templates/app.war`). If this ever becomes fragile, the fully robust
  alternative is to resolve the base resource's URL once and compute relative paths
  against it, rather than string-matching the folder name.

- **Directory entries may appear in results.** Depending on how the archive was built,
  directory entries can show up. The `isReadable()` guard filters them out; filtering on
  `getFilename()` is an alternative.

- **Empty folders are not recreated.** Only files are enumerated, so empty source
  subfolders will not produce corresponding empty destination folders. Create them
  explicitly if required.
