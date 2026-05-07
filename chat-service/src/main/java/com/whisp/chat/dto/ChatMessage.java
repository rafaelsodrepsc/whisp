package com.whisp.chat.dto;

import lombok.Data;

@Data
public class ChatMessage {
    private String roomId;
    private String senderId;
    private String content;
}