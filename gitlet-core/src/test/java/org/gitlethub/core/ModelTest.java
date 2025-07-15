package org.gitlethub.core;

import org.gitlethub.core.model.Blob;
import org.gitlethub.core.model.Repository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.DataFormatException;

import static org.gitlethub.core.utils.FileUtil.readContents;
import static org.junit.jupiter.api.Assertions.*;

public class ModelTest {
    @TempDir
    public static Path tempDir;

    @BeforeAll
    public static void initRepoForTest() {
        System.setProperty("user.dir", tempDir.toString());
        Repository.initRepo();
    }

    @Test
    public void testRepository() throws IOException {
        // Test init to make sure all files are created correctly under CWD/.gitlet
        assertTrue(Files.exists(tempDir.resolve(".gitlet")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/refs")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/refs/heads")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/refs/remotes")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/objects")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/logs")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/HEAD")));
        assertTrue(Files.exists(tempDir.resolve(".gitlet/index")));

        // Test makeObjectFile method
        String emptyId = "adc83b19e793491b1c6ea0fd8b46cd9f32e592fc";
        Repository.makeObjectFile(emptyId).createNewFile();
        assertTrue(Files.exists(tempDir.resolve(".gitlet/objects/ad/c83b19e793491b1c6ea0fd8b46cd9f32e592fc")));
    }

    @Test
    public void testBlob() throws IOException, DataFormatException {
        File testBlobFile =tempDir.resolve("testBlob.txt").toFile();
        Files.write(testBlobFile.toPath(), "Hello World!".getBytes());
        Blob tempBlob = new Blob(testBlobFile);
        tempBlob.makeBlob();
        assertEquals(tempBlob.getUid(), "c57eff55ebc0c54973903af5f72bac72762cf4f4");
        assertTrue(Files.exists(tempDir.resolve(".gitlet/objects/c5/7eff55ebc0c54973903af5f72bac72762cf4f4")));
        byte[] full = tempBlob.getRaw();
        int i = 0;
        while (full[i++] != 0);
        byte[] content = Arrays.copyOfRange(full, i, full.length);
        assertArrayEquals(readContents(testBlobFile), content);
    }
}
