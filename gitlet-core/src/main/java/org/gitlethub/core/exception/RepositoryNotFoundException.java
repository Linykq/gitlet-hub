package org.gitlethub.core.exception;

public class RepositoryNotFoundException extends GitletException {
    public RepositoryNotFoundException(String message) {
        super(message);
    }
}
