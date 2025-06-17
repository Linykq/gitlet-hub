package org.gitlethub.core.exception;

public class MergeConflictException extends GitletException {
    public MergeConflictException(String message) {
        super(message);
    }
}
