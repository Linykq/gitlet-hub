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
    public Blob(File f) {
        this.name = f.getName();
        byte[] content = readContents(f);
        byte[] header = ("blob " + content.length + "\0").getBytes(StandardCharsets.UTF_8);
        this.raw = new byte[content.length + header.length];
        System.arraycopy(header, 0, raw,0, header.length);
        System.arraycopy(content, 0, raw, header.length, content.length);
        this.uid = sha1(this.raw);
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

    public static String getBlobUid(File f) {
        return new Blob(f).getUid();
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
}
