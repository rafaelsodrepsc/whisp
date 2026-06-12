package com.whisp.chat.exception;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(String roomId) {
        super("Room not found: " + roomId);
    }
}