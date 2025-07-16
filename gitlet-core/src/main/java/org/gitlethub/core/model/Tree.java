package org.gitlethub.core.model;

import org.gitlethub.core.exception.ObjectNotFoundException;
import org.gitlethub.core.utils.CompressionUtil;
import org.gitlethub.core.utils.HashUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gitlethub.core.utils.FileUtil.writeObject;

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
        writeObject(outTree, CompressionUtil.compress(raw));
    }

    // TODO: Add this method after finishing Index
    public void makeTree(Index index) {

    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}