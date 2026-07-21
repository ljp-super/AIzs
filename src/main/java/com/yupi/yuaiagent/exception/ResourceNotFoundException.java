package com.yupi.yuaiagent.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String identifier) {
        super(resourceName + " 不存在: " + identifier);
    }
}
