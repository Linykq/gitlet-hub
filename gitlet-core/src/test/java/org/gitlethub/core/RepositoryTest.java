// RepositoryTest.java
package org.gitlethub.core;

import org.gitlethub.core.model.Repository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RepositoryTest {
    @TempDir
    public static Path tempDir;

    @BeforeAll
    public static void initRepoForTest() {
        System.setProperty("user.dir", tempDir.toString());
        Repository.initRepo();
    }

    @Test
    public void testRepositoryInitStructure() {
        assertTrue(Files.exists(tempDir.resolve(".gitlet")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/refs")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/refs/heads")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/refs/remotes")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/objects")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/logs")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/HEAD")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/index")));
    }

    @Test
    public void testMakeObjectFileLayout() throws IOException {
        String emptyId = "adc83b19e793491b1c6ea0fd8b46cd9f32e592fc";
        Repository.makeObjectFile(emptyId).createNewFile();
        assertTrue(Files.exists(tempDir.resolve(".gitlet/objects/ad/c83b19e793491b1c6ea0fd8b46cd9f32e592fc")));
    }
}
