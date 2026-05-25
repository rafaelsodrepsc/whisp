package com.whisp.chat.repository;

import com.whisp.chat.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

    @Query("SELECT r FROM Room r JOIN r.members m WHERE m.userId = :userId")
    List<Room> findAllByMemberUserId(@Param("userId") String userId);
    List<Room> findAll();
}