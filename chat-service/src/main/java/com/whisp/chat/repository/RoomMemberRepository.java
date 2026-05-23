package com.whisp.chat.repository;

import com.whisp.chat.domain.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, String> {

    boolean existsByRoomIdAndUserId(String roomId, String userId);
}