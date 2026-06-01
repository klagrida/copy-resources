# Resource Folder Copier

Copies a folder bundled under `src/main/resources` — including all of its subfolders —
out to a directory on disk, **preserving the original folder structure**.

It works identically in every packaging/runtime, because resources are read through the
classpath (`getInputStream()`), never as `File`s:

| Environment            | Supported | How |
|------------------------|:---------:|-----|
| IDE / exploded classes |     ✅    | `file:` URLs |
| Packaged **JAR**       |     ✅    | `jar:file:...!/...` entries |
| Packaged **WAR**       |     ✅    | `jar:...!/WEB-INF/classes/...` entries |
| **Windows**            |     ✅    | path resolved segment-by-segment; names URL-decoded |

## How it works

- `PathMatchingResourcePatternResolver` expands `classpath*:<base>/**/*` to find every file
  recursively, **including inside archives** (the `classpath*:` prefix scans all locations).
- Each file is read via `resource.getInputStream()` — never `getFile()`, which throws inside
  an archive.
- The path relative to the base folder is recovered from the resource URL, **URL-decoded**
  (so `my%20file.txt` becomes `my file.txt`) and resolved one segment at a time so it maps
  correctly onto any OS, Windows included.
- Directory entries are skipped via `resource.isReadable()`.

### Backup of an existing destination

If the destination folder already exists, it is **renamed aside** to a timestamped backup
sibling before the new copy is written, so the previous contents are never lost:

```
demo-resource-copy/
├── templates/                          ← fresh copy
└── templates.backup-20260601-234618/   ← previous contents, preserved
```

## Configuration

`src/main/resources/application.properties`:

```properties
# Resource folder (under src/main/resources) to copy
app.resource-copy.base-folder=templates
# Root directory on disk where the folder is copied (outside the project).
# The folder name is appended -> /home/khalil/demo-resource-copy/templates
app.resource-copy.dest-dir=/home/khalil/demo-resource-copy
```

## HTTP API

```
PUT /api/resource-folders
```

Copies the configured `base-folder` into `<dest-dir>/<base-folder>`. Modelled as **PUT**
because it is idempotent — repeating it yields the same destination tree (the previous one
is backed up first).

### Example

```bash
curl -X PUT http://localhost:8080/api/resource-folders
```

First call (nothing existed yet):

```json
{
  "baseFolder": "templates",
  "destination": "/home/khalil/demo-resource-copy/templates",
  "filesCopied": 3,
  "backup": null
}
```

Subsequent call (previous folder backed up):

```json
{
  "baseFolder": "templates",
  "destination": "/home/khalil/demo-resource-copy/templates",
  "filesCopied": 3,
  "backup": "/home/khalil/demo-resource-copy/templates.backup-20260601-234618"
}
```

## Build & run

```bash
./mvnw test                              # run the test suite
./mvnw clean package                     # build the jar
java -jar target/demo-0.0.1-SNAPSHOT.jar # run it
```

## Key files

| File | Purpose |
|------|---------|
| `ResourceFolderCopier.java`   | Service: classpath scan, structure-preserving copy, backup |
| `ResourceFolderController.java` | `PUT /api/resource-folders` endpoint |
| `application.properties`       | `base-folder` and `dest-dir` configuration |
| `src/main/resources/templates/` | Sample folder tree used by the tests |

## Caveats

- **Build-time resources only.** Files dropped into the folder *after* the app is packaged
  are not on the classpath and won't be seen. For runtime-added files, scan an external
  directory instead: `resolver.getResources("file:/path/**/*")`.
- **Backups accumulate.** Each run on an existing destination leaves another
  `*.backup-<timestamp>` directory. Add a retention policy if it runs frequently.
- **Empty source folders are not recreated** — only files are enumerated.
