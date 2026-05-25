import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { Sidebar } from '../sidebar/sidebar';
import { MessageArea } from '../message-area/message-area';
import { RoomService, Room } from '../../../core/services/room';
import { MessageService, Message } from '../../../core/services/message';
import { ChatService } from '../../../core/services/chat';
import { Auth } from '../../../core/services/auth';
import { jwtDecode } from 'jwt-decode';

@Component({
  selector: 'app-chat-layout',
  imports: [Sidebar, MessageArea],
  templateUrl: './chat-layout.html',
  styleUrl: './chat-layout.css',
})
export class ChatLayout implements OnInit, OnDestroy {
  rooms = signal<Room[]>([]);
  activeRoomId = signal<string | null>(null);
  currentUserId = '';

  activeRoom = computed(() =>
    this.rooms().find(r => r.id === this.activeRoomId()) ?? null
  );

  constructor(
    private roomService: RoomService,
    private messageService: MessageService,
    public chatService: ChatService,
    private auth: Auth
  ) {
    const token = this.auth.getAccessToken();
    if (token) {
      const decoded: any = jwtDecode(token);
      this.currentUserId = decoded.sub;
    }
  }

  ngOnInit() {
    this.loadRooms();
  }

  allRooms = signal<Room[]>([]);

  loadRooms() {
    this.roomService.list().subscribe({
      next: rooms => {
        this.rooms.set(rooms);
        const savedRoomId = localStorage.getItem('active_room');
        if (savedRoomId && rooms.find(r => r.id === savedRoomId)) {
          this.onRoomSelected(savedRoomId);
        }
      },
    });

    this.roomService.listAll().subscribe({
      next: rooms => this.allRooms.set(rooms),
    });
  }

  ngOnDestroy() {
    this.chatService.disconnect();
  }

  onRoomSelected(roomId: string) {
    this.chatService.disconnect();
    this.activeRoomId.set(roomId);
    localStorage.setItem('active_room', roomId);

    const token = this.auth.getAccessToken()!;
    this.chatService.connect(token, roomId);

    this.messageService.getHistory(roomId).subscribe({
      next: page => this.chatService.messages.set(page.content),
    });
  }

  onRoomCreated(name: string) {
    this.roomService.create(name).subscribe({
      next: room => {
        this.rooms.update(rooms => [...rooms, room]);
        this.onRoomSelected(room.id);
      },
    });
  }

  onMessageSent(content: string) {
    const roomId = this.activeRoomId();
    if (!roomId) return;
    this.chatService.sendMessage(roomId, content);
  }

  onRoomJoined(roomId: string) {
    this.roomService.join(roomId).subscribe({
      next: () => {
        this.loadRooms();
        this.onRoomSelected(roomId);
      },
    });
  }

}
