package org.gitlethub.core;

import org.gitlethub.core.exception.GitletException;
import org.gitlethub.core.model.Blob;
import org.gitlethub.core.model.Index;
import org.gitlethub.core.model.Repository;
import org.junit.jupiter.api.*;

import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit5 tests for Index behaviors:
 * - add(): stage new/modified files, unstage when equal to HEAD, cancel pending deletions
 * - remove(): with/without --cached, require -f when tracked file is modified
 * - persistence via save()/loadOrCreate()
 * - path normalization and idempotency
 * - cleanStageArea() and applyHeadSnapshot()
 */
public class IndexTest {

    @TempDir
    public static Path tempDir;

    @BeforeAll
    public static void initRepo() {
        // Make the repo root point to tempDir, then initialize .gitlet
        System.setProperty("user.dir", tempDir.toString());
        Repository.initRepo();
    }

    @BeforeEach
    public void resetWorkspace() throws IOException {
        // Clean everything except .gitlet, so each test runs in a fresh workspace
        Files.list(tempDir)
                .filter(p -> !p.getFileName().toString().equals(".gitlet"))
                .forEach(p -> {
                    try {
                        if (Files.isDirectory(p)) {
                            Files.walk(p)
                                    .sorted((a, b) -> b.compareTo(a))
                                    .forEach(q -> { try { Files.deleteIfExists(q); } catch (IOException ignored) {}});
                        } else {
                            Files.deleteIfExists(p);
                        }
                    } catch (IOException ignored) {}
                });

        // Reset index file to a fresh empty Index
        new Index().save();
    }

    @Test
    public void testAdd_NewFile_ShouldStageAndCreateBlob() throws IOException {
        File f = tempDir.resolve("a.txt").toFile();
        write(f, "hello\n");

        Index idx = Index.loadOrCreate();
        idx.add(f);

        // Staged for addition with correct blob uid
        String uid = Blob.computeUid(f);
        assertTrue(idx.getAdded().containsValue(uid));

        // The blob object should be written into objects/xx/yyyy...
        String dir = uid.substring(0, 2);
        String tail = uid.substring(2);
        assertTrue(Files.exists(tempDir.resolve(".gitlet/objects/" + dir + "/" + tail)));

        // Not staged for removal
        assertTrue(idx.getRemoved().isEmpty());
    }

    @Test
    public void testAdd_SameAsHEAD_ShouldUnstage() throws IOException {
        File f = tempDir.resolve("b.txt").toFile();
        write(f, "same-as-head");

        // Prepare HEAD snapshot that tracks b.txt with its current uid
        String path = canonicalPath(f);
        String uid = Blob.computeUid(f);

        Map<String, String> head = new HashMap<>();
        head.put(path, uid);

        Index idx = Index.loadOrCreate();
        idx.applyHeadSnapshot(head);

        // add() should detect equality with HEAD and unstage (no entry in 'added')
        idx.add(f);
        assertTrue(idx.getAdded().isEmpty());
        assertTrue(idx.getRemoved().isEmpty());
    }

    @Test
    public void testAdd_CancelsPendingDeletion() throws IOException {
        File f = tempDir.resolve("c.txt").toFile();
        write(f, "keep-me");

        String path = canonicalPath(f);
        String uid = Blob.computeUid(f);

        // HEAD tracks this file
        Map<String, String> head = new HashMap<>();
        head.put(path, uid);

        Index idx = Index.loadOrCreate();
        idx.applyHeadSnapshot(head);

        // Simulate "git rm" planned earlier: staged deletion
        idx.getRemoved(); // read-only view
        // We need to stage deletion via remove(--cached=false); use rmCache to false but file exists
        idx.remove(f, true, true); // stage deletion but keep file (like `git rm --cached` for tracked)
        assertTrue(idx.getRemoved().contains(path));

        // Now add: should cancel deletion
        idx.add(f);
        assertFalse(idx.getRemoved().contains(path));
    }

    @Test
    public void testRemove_StagedAddition_WithCached_ShouldUnstageOnly() throws IOException {
        File f = tempDir.resolve("d.txt").toFile();
        write(f, "new-file");

        Index idx = Index.loadOrCreate();
        idx.add(f);

        // Ensure it's staged for addition first
        String uid = Blob.computeUid(f);
        assertTrue(idx.getAdded().containsValue(uid));
        assertTrue(f.exists());

        // remove with --cached should unstage, but not delete working tree file
        idx.remove(f, false, true);
        assertTrue(idx.getAdded().isEmpty());
        assertTrue(idx.getRemoved().isEmpty());
        assertTrue(f.exists());
    }

    @Test
    public void testRemove_TrackedModified_WithoutForce_ShouldFail() throws IOException {
        File f = tempDir.resolve("e.txt").toFile();
        write(f, "v1");

        String path = canonicalPath(f);
        String uidV1 = Blob.computeUid(f);

        Map<String, String> head = new HashMap<>();
        head.put(path, uidV1);

        Index idx = Index.loadOrCreate();
        idx.applyHeadSnapshot(head);

        // Modify working tree file (now different from HEAD)
        write(f, "v2");

        // Removing without -f should throw, since tracked file is modified
        assertThrows(GitletException.class, () -> idx.remove(f, false, false));

        // Nothing should be staged
        assertTrue(idx.getAdded().isEmpty());
        assertTrue(idx.getRemoved().isEmpty());
        assertTrue(f.exists());
    }

    @Test
    public void testRemove_Tracked_WithForceAndDeleteFile() throws IOException {
        File f = tempDir.resolve("f.txt").toFile();
        write(f, "tracked");

        String path = canonicalPath(f);
        String uid = Blob.computeUid(f);

        Map<String, String> head = new HashMap<>();
        head.put(path, uid);

        Index idx = Index.loadOrCreate();
        idx.applyHeadSnapshot(head);

        // --force and not --cached: stage deletion and remove file from working tree
        idx.remove(f, true, false);
        assertTrue(idx.getRemoved().contains(path));
        assertFalse(f.exists());
    }

    @Test
    public void testRemove_PathspecNoMatch_ShouldThrow() {
        File nonExist = tempDir.resolve("nope.txt").toFile();

        Index idx = Index.loadOrCreate();
        assertThrows(GitletException.class, () -> idx.remove(nonExist, false, false));
    }

    @Test
    public void testCleanStageArea_ShouldClearAddedAndRemoved() throws IOException {
        File a = tempDir.resolve("g1.txt").toFile();
        File b = tempDir.resolve("g2.txt").toFile();
        write(a, "A");
        write(b, "B");

        Index idx = Index.loadOrCreate();
        idx.add(a);

        // Simulate tracked then mark deletion on b
        String pb = canonicalPath(b);
        String uidB = Blob.computeUid(b);

        Map<String, String> head = new HashMap<>();
        head.put(pb, uidB);
        idx.applyHeadSnapshot(head);

        idx.remove(b, true, true); // stage deletion, keep file

        assertFalse(idx.getAdded().isEmpty());
        assertFalse(idx.getRemoved().isEmpty());

        idx.cleanStageArea();
        assertTrue(idx.getAdded().isEmpty());
        assertTrue(idx.getRemoved().isEmpty());
    }

    @Test
    public void testApplyHeadSnapshot_ReplacesTracked() throws IOException {
        File a = tempDir.resolve("h1.txt").toFile();
        File b = tempDir.resolve("h2.txt").toFile();
        write(a, "H1");
        write(b, "H2");

        String pa = canonicalPath(a);
        String pb = canonicalPath(b);

        Map<String, String> head1 = new HashMap<>();
        head1.put(pa, Blob.computeUid(a));

        Map<String, String> head2 = new HashMap<>();
        head2.put(pb, Blob.computeUid(b));

        Index idx = Index.loadOrCreate();
        idx.applyHeadSnapshot(head1);
        assertTrue(idx.getTracked().containsKey(pa));
        assertFalse(idx.getTracked().containsKey(pb));

        idx.applyHeadSnapshot(head2);
        assertFalse(idx.getTracked().containsKey(pa));
        assertTrue(idx.getTracked().containsKey(pb));
    }

    @Test
    public void testPersistence_LoadOrCreate_ShouldRoundTrip() throws IOException {
        File f = tempDir.resolve("persist.txt").toFile();
        write(f, "persist");

        // Stage addition and save
        {
            Index idx = Index.loadOrCreate();
            idx.add(f);
            assertFalse(idx.getAdded().isEmpty());
            // save() is called inside add()
        }

        // Reload index from disk
        Index idx2 = Index.loadOrCreate();
        String uid = Blob.computeUid(f);
        assertTrue(idx2.getAdded().containsValue(uid));
    }

    @Test
    public void testPathNormalization_SameFileDifferentPresentation() throws IOException {
        File base = tempDir.resolve("norm.txt").toFile();
        write(base, "norm");

        // Present the same file via "./norm.txt"
        File dotPath = new File(tempDir.toFile(), "." + File.separator + "norm.txt");

        Index idx = Index.loadOrCreate();
        idx.add(dotPath); // should normalize to the same canonical key as 'base'

        String uid = Blob.computeUid(base);
        assertTrue(idx.getAdded().containsValue(uid));

        // Now remove using the "base" path; it should hit the same normalized key
        idx.remove(base, false, true);
        assertTrue(idx.getAdded().isEmpty());
    }

    // -------------------- helpers --------------------

    private static void write(File f, String content) throws IOException {
        Files.write(f.toPath(), content.getBytes());
    }

    private static String canonicalPath(File f) throws IOException {
        return f.getCanonicalFile().getAbsolutePath();
    }
}
