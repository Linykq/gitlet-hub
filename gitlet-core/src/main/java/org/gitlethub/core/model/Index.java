package org.gitlethub.core.model;

import org.gitlethub.core.exception.GitletException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import static org.gitlethub.core.utils.FileUtil.deleteAllFiles;
import static org.gitlethub.core.utils.FileUtil.readObject;
import static org.gitlethub.core.utils.FileUtil.writeObject;

/**
 * Represents a gitlet index object, used by "gitlet add" & "gitlet rm".
 *
 * Semantics:
 * - added  : path(abs canonical) -> blobUid for files staged for addition/modification.
 * - removed: set of paths(abs canonical) staged for deletion.
 * - tracked: path(abs canonical) -> blobUid snapshot from HEAD (last commit).
 *
 * Notes:
 * - File name/path is the key, NOT the content. Content identity is the Blob uid.
 * - "add" clears a pending deletion of the same path.
 * - "remove --cached" removes from index only (keeps working tree file).
 * - "remove" without --cached removes from index and working tree.
 */
public class Index implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Staged additions/modifications: path -> blob uid */
    private final Map<String, String> added;
    /** Staged removals: set of paths */
    private final Set<String> removed;
    /** HEAD snapshot (tracked files): path -> blob uid */
    private final Map<String, String> tracked;

    /** Instantiate an empty index. */
    public Index() {
        this.added = new HashMap<>();
        this.removed = new HashSet<>();
        this.tracked = new HashMap<>();
    }

    /**
     * Load index from .gitlet/index if exists, otherwise return a new empty index.
     */
    public static Index loadOrCreate() {
        if (Repository.INDEX.exists()) {
            Index idx = readObject(Repository.INDEX, Index.class);
            return (idx != null) ? idx : new Index();
        }
        return new Index();
    }

    /**
     * Stage a file addition/modification:
     * - If file equals HEAD version: unstage it (no-op => ensure not in "added").
     * - If file was staged for deletion (in removed): cancel deletion.
     * - Else: create blob and stage (path -> blob uid) in "added".
     */
    public void add(File file) {
        validateReadableFile(file);

        String path = normalizePath(file);
        String newUid = Blob.computeUid(file);
        String headUid = tracked.get(path);

        // If the file was previously staged for deletion, cancel that.
        removed.remove(path);

        // If content same as HEAD, no need to stage.
        if (newUid.equals(headUid)) {
            added.remove(path);
            save();
            return;
        }

        // Content differs; create and persist blob, then stage.
        Blob blob = new Blob(file);
        blob.makeBlob();
        added.put(path, newUid);
        save();
    }

    /**
     * Remove a file from the index (and optionally from working tree).
     *
     * Behaviors:
     * - If path is neither tracked nor staged for addition: throw "pathspec ... did not match".
     * - For tracked files:
     *    * If working tree modified vs HEAD and not rmForce: refuse (requires -f).
     *    * Stage deletion (add to removed).
     * - For staged additions: unstage the addition (remove from 'added').
     * - If rmCache == false: also delete working tree file; rmCache == true keeps the file.
     */
    public void remove(File file, boolean rmForce, boolean rmCache) throws IOException {
        String path = normalizePath(file);
        boolean exists = file.exists();

        boolean isTracked = tracked.containsKey(path);
        boolean isStagedAdd = added.containsKey(path);

        if (!isTracked && !isStagedAdd) {
            throw new GitletException("fatal: pathspec '" + file.getName() + "' did not match any files");
        }

        // If tracked and working copy exists & modified vs HEAD => need -f
        if (isTracked && exists && !rmForce && isModifiedAgainstHead(file, path)) {
            throw new GitletException("error: '" + file.getName() + "' has local modifications; use -f to force removal");
        }

        // Unstage addition if present
        if (isStagedAdd) {
            added.remove(path);
        }

        // Stage deletion for tracked files (even if file is already missing)
        if (isTracked) {
            removed.add(path);
        }

        // If not --cached, delete the file from working tree (if present)
        if (!rmCache && exists) {
            deleteAllFiles(file.toPath(), Repository.CWD.toPath());
        }

        save();
    }

    /**
     * Clear the staging area (both added & removed).
     * Typically called after a successful commit.
     */
    public void cleanStageArea() {
        added.clear();
        removed.clear();
        save();
    }

    /**
     * Update HEAD snapshot (tracked) after a new commit.
     * Replace the whole tracked map with the provided one.
     */
    public void applyHeadSnapshot(Map<String, String> newTracked) {
        tracked.clear();
        if (newTracked != null && !newTracked.isEmpty()) {
            // Normalize keys to be safe (though upstream ideally already normalized)
            newTracked.forEach((k, v) -> tracked.put(normalizePath(new File(k)), v));
        }
        save();
    }

    /** Persist the index to .gitlet/index. */
    public void save() {
        writeObject(Repository.INDEX, this);
    }

    // ---------------- Getters (read-only views) ----------------

    public Map<String, String> getAdded() {
        return Collections.unmodifiableMap(added);
    }

    public Set<String> getRemoved() {
        return Collections.unmodifiableSet(removed);
    }

    public Map<String, String> getTracked() {
        return Collections.unmodifiableMap(tracked);
    }

    // ---------------- Helpers ----------------

    private static void validateReadableFile(File file) {
        if (file == null) {
            throw new GitletException("File is null");
        }
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            throw new GitletException("File is not a readable regular file: " + file.getPath());
        }
    }

    /** Canonical absolute path for map/set keys to avoid duplicates/symlinks issues. */
    private static String normalizePath(File file) {
        try {
            return file.getCanonicalFile().getAbsolutePath();
        } catch (IOException e) {
            // Fallback to absolute path if canonicalization fails
            return file.getAbsoluteFile().getAbsolutePath();
        }
    }

    /** Compare working tree file content vs HEAD snapshot (tracked). */
    private boolean isModifiedAgainstHead(File file, String normalizedPath) {
        String headUid = tracked.get(normalizedPath);
        if (headUid == null) return false; // not tracked => no HEAD baseline
        String workingUid = Blob.computeUid(file);
        return !workingUid.equals(headUid);
    }
}
