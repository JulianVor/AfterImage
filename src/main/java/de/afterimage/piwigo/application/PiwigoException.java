package de.afterimage.piwigo.application;

public class PiwigoException extends RuntimeException {
    public PiwigoException(String message) { super(message); }
    public PiwigoException(String message, Throwable cause) { super(message, cause); }
}
