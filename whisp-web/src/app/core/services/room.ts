import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface Room {
  id: string;
  name: string;
  type: string;
  createdBy: string;
  createdAt: string;
  memberCount: number;
}

@Injectable({
  providedIn: 'root',
})
export class RoomService {
  private readonly API = 'http://localhost:8082/rooms';

  constructor(private http: HttpClient) {}

  list() {
    return this.http.get<Room[]>(this.API);
  }

  create(name: string) {
    return this.http.post<Room>(this.API, { name });
  }

  join(roomId: string) {
    return this.http.post<void>(`${this.API}/${roomId}/members`, {});
  }

  listAll() {
    return this.http.get<Room[]>(`${this.API}/all`);
  }
}
