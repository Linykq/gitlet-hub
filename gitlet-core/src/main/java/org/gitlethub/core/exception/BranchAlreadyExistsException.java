package org.gitlethub.core.exception;

public class BranchAlreadyExistsException extends GitletException {
    public BranchAlreadyExistsException(String name) {
        super("Branch '" + name + "' already exists");
    }
}
