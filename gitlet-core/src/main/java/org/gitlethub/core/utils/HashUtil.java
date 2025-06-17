package org.gitlethub.core.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class HashUtil {

    private static final int LENGTH = 40;

    /**
     * Calculate SHA-1 Hash of a queue of {@code vals} which may be any mixture of byte[] and String.
     */
    public static String sha1(Object... vals) {
        try {
            MessageDigest md =MessageDigest.getInstance("SHA-1");

            for (Object val : vals) {
                if (val instanceof byte[]) {
                    md.update((byte[]) val);
                } else if (val instanceof String) {
                    md.update(((String) val).getBytes(StandardCharsets.UTF_8));
                } else {

                }
            }

            byte[] hashBytes = md.digest();
            StringBuilder result = new StringBuilder();
            for (byte b : hashBytes) {
                result.append(String.format("%02x", b));
            }

            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Another List version
     */
    public static String sha1(List<Object> vals) {
        return sha1(vals.toArray(new Object[vals.size()]));
    }
}
