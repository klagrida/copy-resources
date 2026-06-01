package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResourceFolderControllerTests {

    // Point the configured destination root at a temp directory for the test run.
    static Path destRoot;

    @DynamicPropertySource
    static void destProperty(DynamicPropertyRegistry registry) throws IOException {
        destRoot = Files.createTempDirectory("controller-dest-root");
        registry.add("app.resource-copy.dest-dir", () -> destRoot.toString());
    }

    @LocalServerPort
    int port;

    @Autowired
    RestTemplateBuilder restTemplateBuilder;

    @Test
    void putCopiesFolderToConfiguredDestination() {
        RestTemplate rest = restTemplateBuilder.build();

        ResponseEntity<Map> response = rest.exchange(
                "http://localhost:" + port + "/api/resource-folders",
                HttpMethod.PUT,
                null,
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().get("filesCopied"));

        Path dest = destRoot.resolve("templates");
        assertTrue(Files.isRegularFile(dest.resolve("a.txt")));
        assertTrue(Files.isRegularFile(dest.resolve("sub/b.txt")));
        assertTrue(Files.isRegularFile(dest.resolve("sub/deep/c.json")));
    }
}
