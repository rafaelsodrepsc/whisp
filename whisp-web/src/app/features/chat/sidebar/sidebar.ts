import { Component, Input, Output, EventEmitter, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Auth } from '../../../core/services/auth';
import { Room } from '../../../core/services/room';

@Component({
  selector: 'app-sidebar',
  imports: [FormsModule],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css',
})
export class Sidebar {
  @Input() rooms: Room[] = [];
  @Input() activeRoomId: string | null = null;
  @Output() roomSelected = new EventEmitter<string>();
  @Output() roomCreated = new EventEmitter<string>();

  showInput = signal(false);
  newRoomName = '';

  constructor(private auth: Auth) {}

  createRoom() {
    const name = this.newRoomName.trim();
    if (!name) return;
    this.roomCreated.emit(name);
    this.cancelCreate();
  }

  cancelCreate() {
    this.newRoomName = '';
    this.showInput.set(false);
  }

  logout() {
    this.auth.logout();
  }

  @Input() allRooms: Room[] = [];
  @Output() roomJoined = new EventEmitter<string>();

  get otherRooms(): Room[] {
    const myRoomIds = new Set(this.rooms.map(r => r.id));
    return this.allRooms.filter(r => !myRoomIds.has(r.id));
  }
}
