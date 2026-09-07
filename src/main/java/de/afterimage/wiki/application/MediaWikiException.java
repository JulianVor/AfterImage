package de.afterimage.wiki.application;

public class MediaWikiException extends RuntimeException {
    public enum Kind {
        NOT_CONFIGURED,
        AUTHENTICATION_FAILED,
        PERMISSION_DENIED,
        EDIT_CONFLICT,
        UNREACHABLE,
        INVALID_RESPONSE,
        API_ERROR,
        NO_PAGES
    }

    private final Kind kind;

    public MediaWikiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public MediaWikiException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
