package org.gitlethub.core;

import org.gitlethub.core.utils.FileUtil;
import org.gitlethub.core.utils.HashUtil;
import org.gitlethub.core.utils.CompressionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.DataFormatException;

import static org.junit.jupiter.api.Assertions.*;

public class UtilsTest {

    /**
     * Test for {@link HashUtil#sha1(Object...)} to ensure SHA-1 works well
     */
    @Test
    public void testSha1() {
        // test normal mixture of byte[] and String
        String sampleString = "Hello World!";
        byte[] sampleBytes = {0, 1, 2, 15, 16, 127, -1, -128};
        String sampleHash = "15c2fddbdad3c03dfd3a0d00c8e26ba20e5b2c85";
        assertEquals(sampleHash, HashUtil.sha1(sampleBytes, sampleString));

        // test empty input
        String empty = "";
        String emptyHash = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
        assertEquals(emptyHash, HashUtil.sha1(empty));
    }

    /**
    * Test for {@link FileUtil#readContents(File)}
    */
    @Test
    public void testReadContents(@TempDir Path tempDir) throws IOException {
        File testDir = tempDir.toFile();
        IllegalArgumentException dirException = assertThrows(IllegalArgumentException.class, () -> FileUtil.readContents(testDir));
        assertEquals("must be a normal file", dirException.getMessage());


        File testFile = tempDir.resolve("testReadContents.txt").toFile();
        Files.write(testFile.toPath(), "Hello World!".getBytes());
        byte[] contents = FileUtil.readContents(testFile);
        assertArrayEquals("Hello World!".getBytes(), contents);
    }

    /**
     * Test for {@link FileUtil#writeContents(File, Object...)}
     */
    @Test
    public void testWriteContents(@TempDir Path tempDir) throws IOException {
        File testDir = tempDir.toFile();
        IllegalArgumentException dirException = assertThrows(IllegalArgumentException.class, () -> FileUtil.writeContents(testDir));
        assertEquals("cannot overwrite directory", dirException.getMessage());

        File testFile = tempDir.resolve("testWriteContents.txt").toFile();
        String sampleString = "Hello World!";
        byte[] sampleBytes = {0, 1, 2, 15, 16, 127, -1, -128};
        FileUtil.writeContents(testFile, sampleString, sampleBytes);
        byte[] sampleStringBytes = sampleString.getBytes();
        byte[] sampleInputBytes = new byte[sampleBytes.length + sampleStringBytes.length];
        System.arraycopy(sampleStringBytes, 0, sampleInputBytes, 0, sampleStringBytes.length);
        System.arraycopy(sampleBytes, 0, sampleInputBytes, sampleStringBytes.length, sampleBytes.length);
        assertArrayEquals(sampleInputBytes, Files.readAllBytes(testFile.toPath()));
    }

    /**
     * A serializable class for {@link #testReadAndWriteObject(Path)} test
     */
    static class Person implements Serializable {
        private final String name;
        private final int age;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Person person = (Person) o;
            return this.name.equals(person.name) && this.age == person.age;
        }

        @Override
        public int hashCode(){
            return name.hashCode() + Integer.hashCode(age);
        }
    }

    /**
     * Test for {@link FileUtil#writeObject(File, Serializable)} and
     * {@link FileUtil#readObject(File, Class)} to ensure correct serialization
     * and deserialization of objects.
     */
    @Test
    public void testReadAndWriteObject(@TempDir Path tempDir) {
        Person origin = new Person("Ethan", 19);
        File testFile = tempDir.resolve("testReadAndWriteObject.txt").toFile();
        FileUtil.writeObject(testFile, origin);
        Person afterWriteAndRead = FileUtil.readObject(testFile, Person.class);
        assertEquals(origin, afterWriteAndRead);
    }

    /**
     * Test for {@link FileUtil#getAllFiles(File)}
     */
    @Test
    public void testGetAllFiles(@TempDir Path tempDir) throws IOException {
        File file1 = tempDir.resolve("file1.txt").toFile();
        File file2 = tempDir.resolve("file2.txt").toFile();
        File subDir = tempDir.resolve("subDir").toFile();

        assertTrue(file1.createNewFile());
        assertTrue(file2.createNewFile());
        assertTrue(subDir.mkdir());

        File subFile = new File(subDir, "hidden.txt");
        assertTrue(subFile.createNewFile());

        List<String> files = FileUtil.getAllFiles(tempDir.toFile());

        assertNotNull(files);
        assertEquals(3, files.size());
        assertTrue(files.contains(file1.getAbsolutePath()));
        assertTrue(files.contains(file2.getAbsolutePath()));
        assertTrue(files.contains(subFile.getAbsolutePath()));
    }

    /**
     * Test for {@link FileUtil#deleteAllFiles(Path, Path)}
     */
    @Test
    public void testDeleteAllFiles(@TempDir Path tempDir) throws IOException {
        File file1 = tempDir.resolve("file1.txt").toFile();
        File file2 = tempDir.resolve("file2.txt").toFile();
        File subDir = tempDir.resolve("subDir").toFile();

        assertTrue(file1.createNewFile());
        assertTrue(file2.createNewFile());
        assertTrue(subDir.mkdir());

        File subFile = new File(subDir, "hidden.txt");
        assertTrue(subFile.createNewFile());

        assertTrue(FileUtil.deleteAllFiles(tempDir.resolve("file1.txt"), tempDir));
        assertTrue(FileUtil.deleteAllFiles(tempDir.resolve("file2.txt"), tempDir));
        assertTrue(FileUtil.deleteAllFiles(tempDir.resolve("subDir"), tempDir));

        List<String> files = FileUtil.getAllFiles(tempDir.toFile());

        assertEquals(0, files.size());
    }

    /**
     * Test for {@link CompressionUtil#compress(byte[])}and {@link CompressionUtil#deCompress(byte[])}
     */
    @Test
    public void testCompressAndDeCompress() throws DataFormatException {
        byte[] original = "Hello World!".getBytes();
        byte[] compressed = CompressionUtil.compress(original);

        assertNotNull(compressed);
        assertArrayEquals(original, CompressionUtil.deCompress(compressed));

        byte[] empty = new byte[0];
        assertArrayEquals(empty, CompressionUtil.deCompress(CompressionUtil.compress(empty)));
    }
}
