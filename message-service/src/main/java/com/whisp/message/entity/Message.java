package com.whisp.message.entity;

import com.whisp.common.event.MessageStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "whisp_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    private String id;

    @Column
    private String roomId;
    @Column
    private String senderId;
    @Column
    private String content;
    @Column
    @Enumerated(EnumType.STRING)
    private MessageStatus status;
    @Column
    private Instant sentAt;

}
