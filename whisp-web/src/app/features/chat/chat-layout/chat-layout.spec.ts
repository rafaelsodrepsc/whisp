import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { apiConfig } from '../../../core/config/api';
import { ChatLayout } from './chat-layout';

describe('ChatLayout', () => {
  let component: ChatLayout;
  let fixture: ComponentFixture<ChatLayout>;
  let http: HttpTestingController;

  beforeEach(async () => {
    // o localStorage do jsdom não existe em todas as versões de Node usadas para os testes
    vi.stubGlobal('localStorage', { getItem: () => null, setItem: () => undefined });

    await TestBed.configureTestingModule({
      imports: [ChatLayout],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChatLayout);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load the rooms without hitting a real server', () => {
    http.expectOne(apiConfig.rooms).flush([]);
    http.expectOne(`${apiConfig.rooms}/all`).flush([]);
    http.verify();
  });
});
