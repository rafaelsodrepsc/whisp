import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { apiConfig } from '../config/api';
import { RoomService } from './room';

describe('RoomService', () => {
  let service: RoomService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RoomService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should list the rooms of the user', () => {
    service.list().subscribe();

    const req = http.expectOne(apiConfig.rooms);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should list all rooms', () => {
    service.listAll().subscribe();

    const req = http.expectOne(`${apiConfig.rooms}/all`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should create a room with its name', () => {
    service.create('geral').subscribe();

    const req = http.expectOne(apiConfig.rooms);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'geral' });
    req.flush({});
  });

  it('should join a room by id', () => {
    service.join('room-1').subscribe();

    const req = http.expectOne(`${apiConfig.rooms}/room-1/members`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
