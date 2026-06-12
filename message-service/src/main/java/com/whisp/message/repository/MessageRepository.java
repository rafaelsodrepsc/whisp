package com.whisp.message.repository;

import com.whisp.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    Page<Message> findByRoomIdOrderBySentAtAsc(String roomId, Pageable pageable);

    List<Message> findBySenderIdOrderBySentAtDesc(String senderId);
}