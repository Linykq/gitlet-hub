package org.gitlethub.core.utils;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class CompressionUtil {
    /**
     * Tool for zip data
     */
    public static byte[] compress(byte[] originalData) {
        Deflater deflater = new Deflater();
        deflater.setInput(originalData);
        deflater.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int len = deflater.deflate(buffer);
            bos.write(buffer, 0, len);
        }
        return bos.toByteArray();
    }

    /**
     * Tool for unzip data
     */
    public static byte[] deCompress(byte[] compressedData) throws DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) {
            int len = inflater.inflate(buffer);
            bos.write(buffer, 0, len);
        }
        return bos.toByteArray();
    }
}
