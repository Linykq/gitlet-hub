package org.gitlethub.core.model;

import org.gitlethub.core.exception.ObjectNotFoundException;
import org.gitlethub.core.utils.CompressionUtil;
import org.gitlethub.core.utils.HashUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gitlethub.core.utils.FileUtil.writeContents;

public class Tree implements Serializable {
    /**
     * Single entry in a Tree (mode + name + sha-1) to present commit tree.
     */
    public static class Entry implements Serializable, Comparable<Entry> {
        public final String mode;
        public final String name;
        public final String sha1;

        public Entry(String mode, String name, String sha1) {
            this.mode = mode;
            this.name = name;
            this.sha1 = sha1;
        }

        @Override
        public int compareTo(Entry o) {
            return this.name.compareTo(o.name);
        }
    }

    /**
     * The name of the tree (the directory)
     */
    private final String name;
    /**
     * Entries of the Tree
     */
    private final List<Entry> entries = new ArrayList<>();
    /**
     * The header + payload before compression
     */
    private byte[] raw;
    /**
     * 40-hex SHA-1 uid of the Tree
     */
    private String uid;

    private static final String BLOB_MODE = "100644";
    private static final String TREE_MODE = "040000";

    /**
     * Instance a new Tree
     */
    public Tree() {
        this.name = "";
    }

    public Tree(String name) {
        this.name = name;
    }

    /**
     * Add entries to the Tree
     */
    public void addEntry(String mode, String name, String sha1) {
        entries.add(new Entry(mode, name, sha1));
    }

    public void addEntry(Blob blob) {
        addEntry(BLOB_MODE, blob.getName(), blob.getUid());
    }

    public void addEntry(Tree tree) throws ObjectStreamException {
        addEntry(TREE_MODE, tree.getName(), tree.getUid());
    }

    /**
     * @return the name of the Tree which is the path name
     */
    public String getName() {
        return name;
    }

    /**
     * Return {@code this.uid}, if not exist firstly {@link #makeTree()}
     */
    public String getUid() throws ObjectStreamException {
        if (uid == null) {
            try {
                makeTree();
            } catch (Exception e) {
                throw new ObjectNotFoundException(e.getMessage());
            }
        }
        return uid;
    }

    /**
     * Return {@code this.code}
     */
    public byte[] getRaw() {
        return raw;
    }

    /**
     * Writes Tree as Object to
     * {@code CWD}/.gitlet/objects/{@link #uid}first 2 digits/{@link #uid}last 38 digits.
     */
    public void makeTree() throws IOException {
        Collections.sort(entries);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (Entry e : entries) {
            payload.write(e.mode.getBytes(StandardCharsets.UTF_8));
            payload.write(' ');
            payload.write(e.name.getBytes(StandardCharsets.UTF_8));
            payload.write(0);
            payload.write(e.sha1.getBytes(StandardCharsets.UTF_8));
        }

        raw = concat(("tree " + payload.size() + '\0').getBytes(StandardCharsets.UTF_8),
                payload.toByteArray());

        uid = HashUtil.sha1(raw);

        File outTree = Repository.makeObjectFile(this.uid);
        if (outTree.exists()) return;
        writeContents(outTree, CompressionUtil.compress(raw));
    }

    /**
     * Build and persist the whole tree object graph from Index state.
     * It applies: finalFiles = tracked - removed + added, all keys converted to
     * repository-relative paths with '/' separators.
     *
     * After building recursively, this Tree instance will mirror the root tree:
     * entries/raw/uid will be copied from the built root.
     */
    public void makeTree(Index index) throws IOException {
        // 1) Build final "relative-path -> blob uid" map
        Map<String, String> finalFiles = new TreeMap<>(); // TreeMap for deterministic traversal

        // tracked (HEAD snapshot)
        index.getTracked().forEach((abs, uid) -> finalFiles.put(toRelPath(abs),uid));

        // removed
        for (String abs : index.getRemoved()) {
            finalFiles.remove(toRelPath(abs));
        }

        // added (override or new)
        index.getAdded().forEach((abs, uid) -> finalFiles.put(toRelPath(abs), uid));

        // 2) Recursively build tree(s) and persist
        Tree builtRoot = buildTreeRecursive(finalFiles, this.name);

        // 3) Mirror results into this instance
        this.entries.clear();
        this.entries.addAll(builtRoot.entries);
        this.raw = builtRoot.raw;
        this.uid = builtRoot.uid;
    }

    /**
     * Recursively build a Tree from a mapping where keys are relative paths (e.g., "src/Main.java").
     * Each recursion level consumes one path segment:
     * - Files at current level become BLOB entries.
     * - Groups by first segment create subtrees (TREE entries).
     */
    private static Tree buildTreeRecursive(Map<String, String> relToBlob, String name) throws IOException {
        Tree t = new Tree(name);

        // Partition into: files at this level vs subdirectories
        Map<String, String> filesHere = new TreeMap<>();
        Map<String, Map<String, String>> byDir = new TreeMap<>();

        for (Map.Entry<String, String> e : relToBlob.entrySet()) {
            String rel = e.getKey();
            String uid = e.getValue();

            int slash = rel.indexOf('/');
            if (slash < 0) {
                // file at this level
                filesHere.put(rel, uid);
            } else {
                String dir = rel.substring(0, slash);
                String rest = rel.substring(slash + 1);
                byDir.computeIfAbsent(dir, k -> new TreeMap<>()).put(rest, uid);
            }
        }

        // Add blob entries
        for (Map.Entry<String, String> fe : filesHere.entrySet()) {
            t.addEntry(BLOB_MODE, fe.getKey(), fe.getValue());
        }

        // Recurse for subdirectories, then add tree entries with child uid
        for (Map.Entry<String, Map<String, String>> de : byDir.entrySet()) {
            String childName = de.getKey();
            Tree child = buildTreeRecursive(de.getValue(), childName);
            // ensure child is materialized (raw/uid written) before referencing
            child.makeTree();
            t.addEntry(TREE_MODE, childName, child.getUid());
        }

        // Materialize this level
        t.makeTree();
        return t;
    }

    /** Convert absolute (canonical) path to repository-relative path with '/' separators. */
    private static String toRelPath(String absOrRel) {
        File base = Repository.CWD.getAbsoluteFile();
        File f = new File(absOrRel);
        String abs;
        try {
            abs = f.getCanonicalFile().getAbsolutePath();
        } catch (IOException e) {
            abs = f.getAbsolutePath();
        }
        String root = base.getAbsolutePath();

        String rel;
        if (abs.startsWith(root)) {
            rel = abs.substring(root.length());
            if (rel.startsWith(File.separator)) rel = rel.substring(1);
        } else {
            // Fallback: if not under repo root, just normalize as best-effort
            rel = f.toPath().normalize().toString();
        }
        return rel.replace(File.separatorChar, '/');
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}