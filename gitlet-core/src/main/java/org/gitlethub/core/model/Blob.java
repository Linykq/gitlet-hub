package org.gitlethub.core.model;

import org.gitlethub.core.exception.GitletException;
import org.gitlethub.core.utils.CompressionUtil;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.DataFormatException;

import static org.gitlethub.core.utils.CompressionUtil.deCompress;
import static org.gitlethub.core.utils.FileUtil.*;
import static org.gitlethub.core.utils.HashUtil.*;

/**
 * Represents a gitlet blob object.
 *
 * Semantics:
 * - Blob hash = SHA-1("blob " + size + '\0' + content), size is content byte length.
 * - Filename is NOT part of the hash; it’s carried only for display and tree construction.
 * - On disk, a blob is stored zlib-compressed at: .gitlet/objects/xx/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 */
public final class Blob implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Optional: original filename; NOT part of the hash */
    private final String name;

    /** raw = header + content, where header = "blob <size>\\0" (bytes) */
    private final byte[] raw;

    /** SHA-1 uid of this blob */
    private final String uid;

    /**
     * Create a Blob from a file by snapshotting its current bytes.
     * @throws GitletException if file is not a readable regular file
     */
    public Blob(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            throw new GitletException("Blob: not a readable file: " + (file == null ? "null" : file.getAbsolutePath()));
        }
        this.name = file.getName();
        byte[] content = readContents(file); // raw bytes in working tree
        byte[] header = headerForSize(content.length);
        this.raw = concat(header, content);
        this.uid = sha1(this.raw);
    }

    /** Internal constructor used by read(uid). */
    private Blob(String name, byte[] raw, String uid) {
        this.name = name;
        this.raw = raw;
        this.uid = uid;
    }

    /**
     * Compute uid from a file (same logic as constructor, but不会持久化).
     */
    public static String computeUid(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            throw new GitletException("computeUid: not a readable file: " + (file == null ? "null" : file.getAbsolutePath()));
        }
        byte[] content = readContents(file);
        byte[] header = headerForSize(content.length);
        return sha1(concat(header, content));
    }

    /**
     * Persist this blob under .gitlet/objects if absent. Atomic move to avoid partial writes.
     * @return uid
     * @throws GitletException on I/O errors
     */
    public String makeBlob() {
        File targetFile = Repository.makeObjectFile(this.uid);
        if (targetFile.exists()) {
            return this.uid;
        }
        try {
            Path target = targetFile.toPath();
            Files.createDirectories(target.getParent());

            // compress once, write to temp, then atomic move
            byte[] compressed = CompressionUtil.compress(this.raw);
            Path tmp = Files.createTempFile(target.getParent(), "obj-", ".tmp");
            try {
                Files.write(tmp, compressed);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    // Fallback if filesystem does not support atomic move
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
            return this.uid;
        } catch (IOException e) {
            throw new GitletException("makeBlob I/O error for uid=" + uid, e);
        }
    }

    /**
     * Read a blob object from .gitlet/objects by uid.
     * name will be null (filename is not part of blob object).
     * @throws GitletException if object missing/corrupted
     */
    public static Blob read(String uid) {
        if (uid == null || uid.length() != 40) {
            throw new GitletException("read blob: invalid uid: " + uid);
        }
        File obj = Repository.makeObjectFile(uid);
        if (!obj.exists() || !obj.isFile()) {
            throw new GitletException("blob object not found: " + uid);
        }
        try {
            byte[] stored = readContents(obj);
            byte[] raw = deCompress(stored);

            int headerEnd = headerEndIndex(raw);
            long size = parseSize(raw, headerEnd);
            int contentLen = raw.length - (headerEnd + 1);
            if (size != contentLen) {
                throw new GitletException("corrupted blob: size mismatch, uid=" + uid +
                        ", headerSize=" + size + ", contentLen=" + contentLen);
            }

            // verify uid
            String calc = sha1(raw);
            if (!calc.equals(uid)) {
                throw new GitletException("corrupted blob: sha1 mismatch, uid=" + uid + ", calc=" + calc);
            }

            return new Blob(null, raw, uid);
        } catch (RuntimeException e) {
            // includes format/parse errors
            throw new GitletException("read blob format error: " + uid, e);
        } catch (DataFormatException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns a defensive copy of raw bytes (header + content). */
    public byte[] getRaw() {
        return Arrays.copyOf(raw, raw.length);
    }

    /** Returns a defensive copy of the content bytes (without header). */
    public byte[] getContent() {
        int headerEnd = headerEndIndex(raw);
        int off = headerEnd + 1;
        return Arrays.copyOfRange(raw, off, raw.length);
    }

    /** Content byte length (from header/content). */
    public int getSize() {
        int headerEnd = headerEndIndex(raw);
        return raw.length - (headerEnd + 1);
    }

    /** uid (40 hex chars). */
    public String getUid() {
        return uid;
    }

    /** Optional filename carried when constructed from a File; may be null when read from object store. */
    public String getName() {
        return name;
    }

    // ---------- object identity & debug ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Blob)) return false;
        Blob other = (Blob) o;
        return this.uid.equals(other.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }

    @Override
    public String toString() {
        return "Blob{uid=" + uid.substring(0, 12) + ", size=" + getSize() + ", name=" + name + "}";
    }

    // ---------- helpers ----------

    private static byte[] headerForSize(long size) {
        // ASCII/UTF-8 is fine; header contains only digits and control char.
        return ("blob " + size + "\0").getBytes(StandardCharsets.UTF_8);
    }

    private static int headerEndIndex(byte[] raw) {
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0) return i;
        }
        throw new GitletException("invalid blob: missing NUL header terminator");
    }

    private static long parseSize(byte[] raw, int headerEnd) {
        String header = new String(raw, 0, headerEnd, StandardCharsets.UTF_8);
        if (!header.startsWith("blob ")) {
            throw new GitletException("invalid blob header: " + header);
        }
        try {
            return Long.parseLong(header.substring(5));
        } catch (NumberFormatException e) {
            throw new GitletException("invalid blob size in header: " + header, e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}
