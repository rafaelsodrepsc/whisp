package com.whisp.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessage {

    @NotBlank
    private String roomId;

    private String senderId;

    @NotBlank
    @Size(max = 2000)
    private String content;
}