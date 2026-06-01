package com.example.demo;

import com.example.demo.ResourceFolderCopier.CopyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ResourceFolderCopierTests {

    @Autowired
    ResourceFolderCopier copier;

    @Test
    void copiesFolderPreservingStructure(@TempDir Path tmp) throws IOException {
        Path dest = tmp.resolve("out"); // does not exist yet -> no backup

        CopyResult result = copier.copyFolder("templates", dest);

        assertEquals(3, result.filesCopied(), "should copy all three files");
        assertNull(result.backup(), "no backup when destination did not exist");

        Path a = dest.resolve("a.txt");
        Path b = dest.resolve("sub/b.txt");
        Path c = dest.resolve("sub/deep/c.json");

        assertTrue(Files.isRegularFile(a), "a.txt should exist at root");
        assertTrue(Files.isRegularFile(b), "b.txt should exist under sub/");
        assertTrue(Files.isRegularFile(c), "c.json should exist under sub/deep/");

        assertEquals("top-level template", Files.readString(a).trim());
        assertEquals("nested template b", Files.readString(b).trim());
        assertTrue(Files.readString(c).contains("\"name\": \"c\""));
    }

    @Test
    void backsUpExistingDestination(@TempDir Path tmp) throws IOException {
        Path dest = tmp.resolve("out");
        Files.createDirectories(dest);
        Files.writeString(dest.resolve("old-file.txt"), "previous contents");

        CopyResult result = copier.copyFolder("templates", dest);

        // the previous destination was preserved under a backup
        assertNotNull(result.backup(), "an existing destination should be backed up");
        assertTrue(Files.isRegularFile(result.backup().resolve("old-file.txt")),
                "backup should contain the previous files");

        // the new destination holds only the freshly copied tree, not the old file
        assertEquals(3, result.filesCopied());
        assertTrue(Files.isRegularFile(dest.resolve("a.txt")));
        assertTrue(Files.notExists(dest.resolve("old-file.txt")),
                "old file must not leak into the fresh destination");
    }
}
