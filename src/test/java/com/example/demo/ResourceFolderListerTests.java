package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
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
                List.of("a.txt", "sub/b.txt", "sub/deep/c.json"),
                files);
    }
}
