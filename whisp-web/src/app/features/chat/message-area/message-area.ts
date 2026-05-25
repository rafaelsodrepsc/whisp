import { Component, Input, Output, EventEmitter, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Room } from '../../../core/services/room';
import { Message } from '../../../core/services/message';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-message-area',
  imports: [FormsModule, DatePipe],
  templateUrl: './message-area.html',
  styleUrl: './message-area.css',
})
export class MessageArea implements AfterViewChecked {
  @Input() room: Room | null = null;
  @Input() messages: Message[] = [];
  @Input() currentUserId = '';
  @Output() messageSent = new EventEmitter<string>();

  @ViewChild('scrollContainer') scrollContainer!: ElementRef;

  content = '';

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  send() {
    const text = this.content.trim();
    if (!text) return;
    this.messageSent.emit(text);
    this.content = '';
  }

  private scrollToBottom() {
    try {
      const el = this.scrollContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    } catch {}
  }
}
