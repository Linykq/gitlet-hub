package org.gitlethub.core.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class FileUtil {

    /**
     * Return the entire contents of {@code file} as a byte array.
     * {@code file} must be a normal file.
     * Throws IllegalArgumentException in case of problems.
     */
    public static byte[] readContents(File file) {
        if (!file.isFile()) {
            throw new IllegalArgumentException("must be a normal file");
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * Another Version of {@link #readContents} return String instead of byte[]
     */
    public static String readContentsAsString(File file) {
        return new String(readContents(file), StandardCharsets.UTF_8);
    }

    /**
     * Write {@code contents} which may be any mixture of byte[] and String into {@code file}
     * {@code file} must be a normal file.
     * Throws IllegalArgumentException in case of problems.
     */
    public static void writeContents(File file, Object... contents) {
        try {
            if (file.isDirectory()) {
                throw new IllegalArgumentException("cannot overwrite directory");
            }
            BufferedOutputStream str = new BufferedOutputStream(Files.newOutputStream(file.toPath()));
            for (Object content : contents) {
                if (content instanceof byte[]) {
                    str.write((byte[]) content);
                } else {
                    str.write(((String) content).getBytes(StandardCharsets.UTF_8));
                }
            }
            str.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deserialization read {@code file}, casting it to {@code expectedClass} and return this object
     */
    public static <T extends Serializable> T readObject(File file, Class<T> expectedClass) {
        try (ObjectInputStream stream = new ObjectInputStream(new FileInputStream(file))) {
            return expectedClass.cast(stream.readObject());
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            throw new IllegalArgumentException("Failed to read object to file", e);
        }
    }

    /**
     * Serialized write {@code obj} to {@code file}
     */
    public static void writeObject(File file, Serializable obj) {
        try (ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(file));) {
            stream.writeObject(obj);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to write object from file", e);
        }
    }

    /**
     * Filter out all plain files (not dir)
     */
    private static final FilenameFilter NOT_DIR_FILES = (dir, name) -> {
        return new File(dir, name).isFile();
    };

    /**
     * Returns a list of names of all files directly under {@code dir}
     */
    public static List<String> getDirectFiles(File dir) {
        String[] files = dir.list(NOT_DIR_FILES);
        if (files == null) {
            return null;
        } else {
            Arrays.sort(files);
            return Arrays.asList(files);
        }
    }

    /**
     * Deletes a {@code file} if it exists under {@code .gitlet} directory
     * and is not a directory.
     */
    public static boolean restrictedDelete(File file) {
        if (!(new File(file.getParentFile(), ".gitlet")).isDirectory()) {
            throw new IllegalArgumentException("not .gitlet working directory");
        }
        if (!file.isDirectory()) {
            return file.delete();
        } else {
            return false;
        }
    }

    /**
     * String version of {@link #restrictedDelete(File)}
     */
    public static boolean restrictedDelete(String file) {
        return restrictedDelete(new File(file));
    }

    /**
     * Return the combination of {@code first}'s path and a set of {@code others} as a path
     */
    public static File join(File first, String... others) {
        return Paths.get(first.getPath(), others).toFile();
    }

    /**
     * String input version of {@link #join(File, String...)}
     */
    public static File join(String first, String... others) {
        return Paths.get(first, others).toFile();
    }
}
