package com.whisp.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessage {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String roomId;

    private String senderId;

    @NotBlank
    @Size(max = 2000)
    private String content;
}