// BlobTest.java
package org.gitlethub.core;

import org.gitlethub.core.model.Blob;
import org.gitlethub.core.model.Repository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.gitlethub.core.utils.FileUtil.readContents;
import static org.junit.jupiter.api.Assertions.*;

public class BlobTest {
    @TempDir
    public static Path tempDir;

    @BeforeAll
    public static void initRepoForTest() {
        System.setProperty("user.dir", tempDir.toString());
        Repository.initRepo();
    }

    @BeforeEach
    public void cleanupFiles() throws IOException {
        Files.list(tempDir)
                .filter(path -> !path.getFileName().toString().equals(".gitlet"))
                .forEach(path -> {
                    try {
                        if (Files.isDirectory(path)) {
                            Files.walk(path)
                                    .sorted((p1, p2) -> p2.compareTo(p1))
                                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                        } else {
                            Files.deleteIfExists(path);
                        }
                    } catch (IOException ignored) {}
                });
    }

    @Test
    public void testBlob_BasicMakeAndLayout() throws IOException {
        File f = tempDir.resolve("testBlob.txt").toFile();
        Files.write(f.toPath(), "Hello World!".getBytes()); // 12 bytes

        Blob blob = new Blob(f);
        assertEquals("testBlob.txt", blob.getName());
        assertEquals(12, blob.getSize());
        assertEquals("c57eff55ebc0c54973903af5f72bac72762cf4f4", blob.getUid());

        blob.makeBlob();
        assertTrue(Files.exists(tempDir.resolve(".gitlet/objects/c5/7eff55ebc0c54973903af5f72bac72762cf4f4")));

        assertArrayEquals(readContents(f), blob.getContent());

        byte[] raw = blob.getRaw();
        raw[raw.length - 1] ^= 0x1;
        assertArrayEquals(readContents(f), blob.getContent());

        assertDoesNotThrow(blob::makeBlob);
    }

    @Test
    public void testBlob_ReadBackAndEquality() throws IOException {
        File f = tempDir.resolve("foo.bin").toFile();
        byte[] bytes = new byte[]{0x00, 0x01, (byte)0xFF, 0x7F, 0x20};
        Files.write(f.toPath(), bytes);

        Blob b1 = new Blob(f);
        b1.makeBlob();

        Blob b2 = Blob.read(b1.getUid());
        assertEquals(b1.getUid(), b2.getUid());
        assertEquals(bytes.length, b2.getSize());
        assertNull(b2.getName());
        assertArrayEquals(bytes, b2.getContent());

        Blob b3 = new Blob(f);
        assertEquals(b1.getUid(), b3.getUid());
        assertEquals(b1, b3);
        assertEquals(b1.hashCode(), b3.hashCode());
    }

    @Test
    public void testBlob_ComputeUid() throws IOException {
        File f = tempDir.resolve("bar.txt").toFile();
        String text = "some content\nwith lines\n";
        Files.write(f.toPath(), text.getBytes());

        Blob b = new Blob(f);
        assertEquals(Blob.computeUid(f), b.getUid());
    }

    @Test
    public void testBlob_ContentHeaderShape() throws IOException {
        File f = tempDir.resolve("shape.txt").toFile();
        String text = "ABCD";
        Files.write(f.toPath(), text.getBytes()); // 4 bytes

        Blob b = new Blob(f);
        byte[] raw = b.getRaw();

        int i = 0;
        while (raw[i++] != 0) { /* find NUL */ }
        byte[] header = Arrays.copyOfRange(raw, 0, i); // include NUL
        byte[] content = Arrays.copyOfRange(raw, i, raw.length);

        assertArrayEquals(text.getBytes(), content);
        assertEquals("blob 4\0", new String(header));
    }
}
