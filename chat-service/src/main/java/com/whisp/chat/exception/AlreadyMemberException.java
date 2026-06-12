package com.whisp.chat.exception;

public class AlreadyMemberException extends RuntimeException {
    public AlreadyMemberException(String roomId) {
        super("User is already a member of room: " + roomId);
    }
}