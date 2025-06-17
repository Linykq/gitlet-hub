package org.gitlethub.core.exception;

/**
 *
 */
public class GitletException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 无参构造：少数情况直接 throw new GitletException(); */
    public GitletException() {
        super();
    }

    /** 仅描述信息 */
    public GitletException(String message) {
        super(message);
    }

    /** 描述 + 原因 */
    public GitletException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 仅包装原因（保留原始堆栈） */
    public GitletException(Throwable cause) {
        super(cause);
    }
}