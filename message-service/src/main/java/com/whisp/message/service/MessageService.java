package com.whisp.message.service;

import com.whisp.message.dto.MessageResponse;
import com.whisp.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public Page<MessageResponse> findByRoom(String roomId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return messageRepository.findByRoomIdOrderBySentAtAsc(roomId, pageable)
                .map(MessageResponse::from);
    }
}