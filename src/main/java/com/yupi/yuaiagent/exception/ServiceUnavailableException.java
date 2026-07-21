package com.yupi.yuaiagent.exception;

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String serviceName, String reason) {
        super(serviceName + " 不可用: " + reason);
    }
}
