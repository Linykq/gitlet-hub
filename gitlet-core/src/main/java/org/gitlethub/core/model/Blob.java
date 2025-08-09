package org.gitlethub.core.model;

import org.gitlethub.core.utils.CompressionUtil;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;

import static org.gitlethub.core.utils.FileUtil.*;
import static org.gitlethub.core.utils.HashUtil.*;

public class Blob implements Serializable {
    /**
     * The name of the file of the Blob
     */
    private final String name;
    /**
     * The content includes the header to be zlib into the blob
     */
    private final byte[] raw;
    /**
     * The SHA-1 uid of the blob object.
     */
    private final String uid;

    /**
     * Instantiate a blob object with a File.
     * A blob means a snapshot of tracked file.
     */
    public Blob(File file) {
        this.name = file.getName();
        byte[] content = readContents(file);
        byte[] header = ("blob " + content.length + "\0").getBytes(StandardCharsets.UTF_8);
        this.raw = concat(header, content);
        this.uid = sha1(this.raw);
    }

    public static String computeUid(File file) {
        byte[] content = readContents(file);
        byte[] header = ("blob " + content.length + "\0").getBytes(StandardCharsets.UTF_8);
        return sha1(concat(header, content));
    }

    /**
     * Writes Blob as Object to
     * {@code CWD}/.gitlet/objects/{@link #uid}first 2 digits/{@link #uid}last 38 digits.
     */
    public String makeBlob() {
        File outBlob = Repository.makeObjectFile(this.uid);
        if (outBlob.exists()) return this.uid;
        writeObject(outBlob, CompressionUtil.compress(raw));
        return this.uid;
    }

    /**
     * @return raw includes the header and content
     */
    public byte[] getRaw() {
        return this.raw;
    }

    /**
     * @return uid of the object
     */
    public String getUid() {
        return uid;
    }

    /**
     * @return name of the file of the Blob
     */
    public String getName() {
        return name;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}
