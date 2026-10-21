import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { apiConfig } from '../config/api';
import { MessageService } from './message';

describe('MessageService', () => {
  let service: MessageService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MessageService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should request the history of a room with default paging', () => {
    service.getHistory('room-1').subscribe();

    const req = http.expectOne(`${apiConfig.messages}/room-1/messages?page=0&size=50`);
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 });
  });

  it('should pass the requested page and size', () => {
    service.getHistory('room-1', 2, 20).subscribe();

    http.expectOne(`${apiConfig.messages}/room-1/messages?page=2&size=20`).flush({});
  });
});
