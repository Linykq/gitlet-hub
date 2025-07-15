package org.gitlethub.core.model;

import org.gitlethub.core.utils.*;

import java.io.Serializable;
import java.io.File;
import java.util.List;

import static org.gitlethub.core.utils.FileUtil.*;

/**
 *.gitlet/
 * <br>├── HEAD
 * <br>├── config
 * <br>├── index
 * <br>├── objects/
 * <br>│   ├── info/
 * <br>│   ├── pack/
 * <br>│   └── ab/…
 * <br>│       └── cdef1234…
 * <br>├── refs/
 * <br>│   ├── heads/
 * <br>│   │   └── master
 * <br>│   └── tags/
 * <br>│       └── v1.0
 * <br>├── logs/
 * <br>│   ├── HEAD
 * <br>│   └── refs/
 * <br>│       └── heads/
 * <br>│           └── master
 * <br>├── hooks/
 * <br>│   ├── pre-commit.sample
 * <br>│   └── post-commit.sample
 * <br>│
 * <br>└── packed-refs
 */

public class Repository implements Serializable {
    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /**
     * The references' directory.
     */
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    /**
     * The branch directory.
     */
    public static final File BRANCHES_DIR = join(REFS_DIR, "heads");
    /**
     * The remotes directory
     */
    public static final File REMOTES_DIR = join(REFS_DIR, "remotes");
    /**
     * The objects directory which stored blobs and commits
     */
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    /**
     * The logs directory
     */
    public static final File LOGS_DIR = join(GITLET_DIR, "logs");
    /**
     * The HEAD pointer.
     */
    public static final File HEAD = join(GITLET_DIR, "HEAD");
    /**
     * The index object which stored refs of
     * added files and removed files
     */
    public static final File INDEX = join(GITLET_DIR, "index");

    public static void initRepo() {
        List<File> dirs = List.of(GITLET_DIR, REFS_DIR, OBJECTS_DIR, BRANCHES_DIR, REMOTES_DIR, LOGS_DIR);
        dirs.forEach(File::mkdir);
        Branch main = new Branch("main", "");
        writeContents(HEAD, "ref: refs/heads/main\n");
        writeObject(INDEX, new Index());
    }

    /**
     * Deletes all files in DIR
     */
    public static void clean(File dir) {
        List<String> files = getDirectFiles(dir);
        if (files != null) {
            files.forEach(n -> join(dir, n).delete());
        }
    }
    /**
     * Get directory of a commit object or a blob object with its first two id.
     */
    public static File getObjectsDir(String id) {
        return join(OBJECTS_DIR, id.substring(0, 2));
    }

    /**
     * Get filename of a commit object or a blob object with its last thirty-eight id.
     */
    public static String getObjectName(String id) {
        return id.substring(2);
    }

    /**
     * Get filepath of a commit object or a blob object with its id.
     */
    public static File makeObjectFile(String id) {
        File outDir = getObjectsDir(id);
        outDir.mkdir();
        return join(outDir, getObjectName(id));
    }
}
