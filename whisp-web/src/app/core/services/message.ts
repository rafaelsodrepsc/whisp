import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface Message {
  id: string;
  roomId: string;
  senderId: string;
  senderUsername: string;
  content: string;
  status: string;
  sentAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({
  providedIn: 'root',
})
export class MessageService {
  private readonly API = 'http://localhost:8083/rooms';

  constructor(private http: HttpClient) {}

  getHistory(roomId: string, page = 0, size = 50) {
    return this.http.get<PageResponse<Message>>(
      `${this.API}/${roomId}/messages?page=${page}&size=${size}`
    );
  }
}
