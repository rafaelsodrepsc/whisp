import { TestBed } from '@angular/core/testing';

import { ChatService } from './chat';

describe('ChatService', () => {
  let service: ChatService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChatService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should ignore sendMessage while disconnected', () => {
    expect(() => service.sendMessage('room-1', 'oi')).not.toThrow();
    expect(service.messages()).toEqual([]);
  });

  it('should clear messages on disconnect', () => {
    service.messages.set([
      { id: '1', roomId: 'r', senderId: 'u', senderUsername: 'a', content: 'x', status: 'SENT', sentAt: '' },
    ]);

    service.disconnect();

    expect(service.messages()).toEqual([]);
  });
});
