import { Injectable, signal } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Message } from './message';

@Injectable({
  providedIn: 'root',
})
export class ChatService {
  private client: Client | null = null;
  messages = signal<Message[]>([]);

  connect(token: string, roomId: string) {
    this.messages.set([]);

    this.client = new Client({
      brokerURL: 'ws://localhost:8082/ws/websocket',
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => {
        this.client!.subscribe(`/topic/chat/${roomId}`, (frame: IMessage) => {
          const msg: Message = JSON.parse(frame.body);
          this.messages.update(msgs => {
            const existingIndex = msgs.findIndex(m => m.id === msg.id);
            if (existingIndex === -1) {
              return [...msgs, msg];
            }
            return msgs.map((m, i) => (i === existingIndex ? msg : m));
          });
        });
      },
    });

    this.client.activate();
  }

  sendMessage(roomId: string, content: string) {
    if (!this.client?.connected) return;

    this.client.publish({
      destination: `/app/chat/${roomId}`,
      body: JSON.stringify({ roomId, content }),
    });
  }

  disconnect() {
    this.client?.deactivate();
    this.client = null;
    this.messages.set([]);
  }
}
