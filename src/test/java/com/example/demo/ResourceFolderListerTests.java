package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ResourceFolderListerTests {

    @Autowired
    ResourceFolderLister lister;

    @Test
    void listsRelativePathsStartingAfterBaseFolder() throws IOException {
        List<String> files = lister.listFiles("templates");

        assertEquals(
                Arrays.asList("a.txt", "sub/b.txt", "sub/deep/c.json"),
                files);
    }

    @Test
    void listsDirectoriesAndFilesParentFirst() throws IOException {
        List<ResourceFolderLister.Entry> entries = lister.listEntries("templates");

        assertEquals(
                Arrays.asList(
                        new ResourceFolderLister.Entry("a.txt", false),
                        new ResourceFolderLister.Entry("sub", true),
                        new ResourceFolderLister.Entry("sub/b.txt", false),
                        new ResourceFolderLister.Entry("sub/deep", true),
                        new ResourceFolderLister.Entry("sub/deep/c.json", false)),
                entries);
    }
}
