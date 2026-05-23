package com.whisp.chat.controller;

import com.whisp.chat.dto.CreateRoomRequest;
import com.whisp.chat.dto.RoomResponse;
import com.whisp.chat.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request,
                                               Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.create(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> list(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(roomService.listByUser(userId));
    }

    @PostMapping("/{roomId}/members")
    public ResponseEntity<Void> join(@PathVariable String roomId, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        roomService.join(roomId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}