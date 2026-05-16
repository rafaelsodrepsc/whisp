package com.whisp.message.entity;

import com.whisp.common.event.MessageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "whisp_message")
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    @EqualsAndHashCode.Include
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
