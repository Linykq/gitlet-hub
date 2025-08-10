package org.gitlethub.core;

import org.gitlethub.core.model.Blob;
import org.gitlethub.core.model.Index;
import org.gitlethub.core.model.Repository;
import org.gitlethub.core.model.Tree;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.DataFormatException;

import static org.gitlethub.core.utils.CompressionUtil.deCompress;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit5 tests for Tree:
 * - Build tree from Index (tracked - removed + added)
 * - Nested directories recursion and deterministic ordering
 * - Empty tree behavior and object-store writes
 * - Round-trip parsing of raw tree payload (mode, name, sha1)
 */
public class TreeTest {

    @TempDir
    public static Path tempDir;

    @BeforeAll
    public static void initRepo() {
        // Initialize .gitlet under temporary directory
        System.setProperty("user.dir", tempDir.toString());
        Repository.initRepo();
    }

    @BeforeEach
    public void cleanWorkspace() throws IOException {
        // Remove everything except .gitlet so each test runs cleanly
        Files.list(tempDir)
                .filter(p -> !p.getFileName().toString().equals(".gitlet"))
                .forEach(p -> {
                    try {
                        if (Files.isDirectory(p)) {
                            Files.walk(p)
                                    .sorted((a, b) -> b.compareTo(a))
                                    .forEach(q -> { try { Files.deleteIfExists(q); } catch (IOException ignored) {} });
                        } else {
                            Files.deleteIfExists(p);
                        }
                    } catch (IOException ignored) {}
                });
        // Reset index file
        new Index().save();
    }

    @Test
    public void testSingleFileRootTree() throws IOException {
        // Create one file at repository root
        File f = tempDir.resolve("hello.txt").toFile();
        write(f, "Hello World!\n");
        String blobUid = Blob.computeUid(f);

        // Stage via Index so blob is persisted
        Index idx = Index.loadOrCreate();
        idx.add(f);

        // Build tree from index
        Tree root = new Tree();
        root.makeTree(idx);

        // Root tree must exist in object store
        assertTrue(treeObjectExists(root.getUid()));

        // Parse root raw and validate a single BLOB entry with correct sha1
        List<TreeEntry> entries = parseTreeRaw(root.getRaw());
        assertEquals(1, entries.size());
        TreeEntry e = entries.get(0);
        assertEquals("100644", e.mode);
        assertEquals("hello.txt", e.name);
        assertEquals(blobUid, e.sha1);
    }

    @Test
    public void testNestedDirectoriesAndOrdering() throws IOException, DataFormatException {
        // Create a small hierarchy:
        // README.md
        // src/A.java
        // src/util/B.java
        Path src = tempDir.resolve("src");
        Path util = src.resolve("util");
        Files.createDirectories(util);

        File readme = tempDir.resolve("README.md").toFile();
        File a = src.resolve("A.java").toFile();
        File b = util.resolve("B.java").toFile();

        write(readme, "readme\n");
        write(a, "class A {}\n");
        write(b, "class B {}\n");

        String uidReadme = Blob.computeUid(readme);
        String uidA = Blob.computeUid(a);
        String uidB = Blob.computeUid(b);

        Index idx = Index.loadOrCreate();
        idx.add(readme);
        idx.add(a);
        idx.add(b);

        // Build tree
        Tree root = new Tree();
        root.makeTree(idx);
        assertTrue(treeObjectExists(root.getUid()));

        // Parse root entries: expect README.md (blob) and src (tree), in lexicographic order
        List<TreeEntry> rootEntries = parseTreeRaw(root.getRaw());
        assertEquals(2, rootEntries.size());
        assertEquals("README.md", rootEntries.get(0).name);
        assertEquals("100644", rootEntries.get(0).mode);
        assertEquals(uidReadme, rootEntries.get(0).sha1);
        assertEquals("src", rootEntries.get(1).name);
        assertEquals("040000", rootEntries.get(1).mode);

        // Read 'src' subtree by uid and check entries: A.java (blob), util (tree)
        String srcUid = rootEntries.get(1).sha1;
        byte[] srcRaw = readTreeRawFromStore(srcUid);
        List<TreeEntry> srcEntries = parseTreeRaw(srcRaw);
        assertEquals(2, srcEntries.size());
        assertEquals("A.java", srcEntries.get(0).name);
        assertEquals("100644", srcEntries.get(0).mode);
        assertEquals(uidA, srcEntries.get(0).sha1);
        assertEquals("util", srcEntries.get(1).name);
        assertEquals("040000", srcEntries.get(1).mode);

        // Read 'util' subtree and check entry B.java
        String utilUid = srcEntries.get(1).sha1;
        byte[] utilRaw = readTreeRawFromStore(utilUid);
        List<TreeEntry> utilEntries = parseTreeRaw(utilRaw);
        assertEquals(1, utilEntries.size());
        assertEquals("B.java", utilEntries.get(0).name);
        assertEquals("100644", utilEntries.get(0).mode);
        assertEquals(uidB, utilEntries.get(0).sha1);
    }

    @Test
    public void testTrackedMinusRemovedPlusAdded() throws IOException {
        // Prepare HEAD tracking "old.txt"
        File oldF = tempDir.resolve("old.txt").toFile();
        write(oldF, "old");
        String oldPath = canonical(oldF);
        String oldUid = Blob.computeUid(oldF);

        // Prepare a new file to be added "new.txt"
        File newF = tempDir.resolve("new.txt").toFile();
        write(newF, "new");
        String newUid = Blob.computeUid(newF);

        Index idx = Index.loadOrCreate();

        // Apply HEAD snapshot (tracked: old.txt)
        Map<String, String> head = new HashMap<>();
        head.put(oldPath, oldUid);
        idx.applyHeadSnapshot(head);

        // Stage removal of old.txt (keep working tree)
        idx.remove(oldF, true, true);

        // Stage addition of new.txt
        idx.add(newF);

        // Build tree
        Tree root = new Tree();
        root.makeTree(idx);

        // Root entries should include ONLY new.txt
        List<TreeEntry> entries = parseTreeRaw(root.getRaw());
        assertEquals(1, entries.size());
        assertEquals("new.txt", entries.get(0).name);
        assertEquals("100644", entries.get(0).mode);
        assertEquals(newUid, entries.get(0).sha1);
    }

    @Test
    public void testDeterministicOrderingAndStableUid() throws IOException {
        // Two files: a.txt and b.txt
        File a = tempDir.resolve("a.txt").toFile();
        File b = tempDir.resolve("b.txt").toFile();
        write(a, "A");
        write(b, "B");

        // First build: add a then b
        Index idx1 = Index.loadOrCreate();
        idx1.add(a);
        idx1.add(b);
        Tree t1 = new Tree();
        t1.makeTree(idx1);
        String uid1 = t1.getUid();

        // New index: add b then a (different staging order)
        new Index().save(); // reset index file
        Index idx2 = Index.loadOrCreate();
        idx2.add(b);
        idx2.add(a);
        Tree t2 = new Tree();
        t2.makeTree(idx2);
        String uid2 = t2.getUid();

        // Tree uid must be stable regardless of staging order
        assertEquals(uid1, uid2);

        // And entries should be lexicographically sorted by name: a.txt then b.txt
        List<TreeEntry> entries = parseTreeRaw(t2.getRaw());
        assertEquals(2, entries.size());
        assertEquals("a.txt", entries.get(0).name);
        assertEquals("b.txt", entries.get(1).name);
    }

    @Test
    public void testEmptyTree() throws IOException {
        // Index is empty: no tracked, no added, no removed
        Index idx = Index.loadOrCreate(); // after @BeforeEach it's empty

        Tree t = new Tree();
        t.makeTree(idx);

        // Empty tree raw is "tree 0\0" (no payload)
        byte[] raw = t.getRaw();
        String header = readHeader(raw);
        assertEquals("tree 0", header);

        // Empty tree object exists in store and uid equals Git's empty tree id
        assertTrue(treeObjectExists(t.getUid()));
        assertEquals("4b825dc642cb6eb9a060e54bf8d69288fbee4904", t.getUid());
    }

    // ---------- helpers ----------

    private static void write(File f, String s) throws IOException {
        Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonical(File f) throws IOException {
        return f.getCanonicalFile().getAbsolutePath();
    }

    private static boolean treeObjectExists(String uid) {
        Path path = tempDir.resolve(".gitlet/objects/" + uid.substring(0, 2) + "/" + uid.substring(2));
        return Files.exists(path);
    }

    private static byte[] readTreeRawFromStore(String uid) throws IOException, DataFormatException {
        Path path = tempDir.resolve(".gitlet/objects/" + uid.substring(0, 2) + "/" + uid.substring(2));
        byte[] compressed = Files.readAllBytes(path);
        return deCompress(compressed);
    }

    private static String readHeader(byte[] raw) {
        int i = 0;
        while (i < raw.length && raw[i] != 0) i++;
        return new String(raw, 0, i, StandardCharsets.UTF_8); // e.g., "tree 42"
    }

    /**
     * Parse a tree's raw bytes (header + payload) into a list of entries.
     * Format: <mode> ' ' <name> '\0' <40-byte hex sha1> [repeat...]
     */
    private static List<TreeEntry> parseTreeRaw(byte[] raw) {
        List<TreeEntry> out = new ArrayList<>();
        int i = 0;

        // skip header "tree <size>\0"
        while (i < raw.length && raw[i] != 0) i++;
        i++; // skip NUL

        while (i < raw.length) {
            // mode
            int start = i;
            while (i < raw.length && raw[i] != ' ') i++;
            String mode = new String(raw, start, i - start, StandardCharsets.UTF_8);
            i++; // skip space

            // name
            start = i;
            while (i < raw.length && raw[i] != 0) i++;
            String name = new String(raw, start, i - start, StandardCharsets.UTF_8);
            i++; // skip NUL

            // sha1 (40 hex chars)
            if (i + 40 > raw.length) {
                fail("Malformed tree: insufficient bytes for sha1");
            }
            String sha = new String(raw, i, 40, StandardCharsets.UTF_8);
            i += 40;

            out.add(new TreeEntry(mode, name, sha));
        }
        return out;
    }

    private static class TreeEntry {
        final String mode;
        final String name;
        final String sha1;

        TreeEntry(String mode, String name, String sha1) {
            this.mode = mode;
            this.name = name;
            this.sha1 = sha1;
        }
    }
}
