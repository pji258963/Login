package com.example.login.exception;

public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(String username) {
        super("使用者名稱已被使用: " + username);
    }
}
