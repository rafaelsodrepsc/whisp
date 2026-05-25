import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

@Injectable({
  providedIn: 'root',
})
export class Auth {
  private readonly API = 'http://localhost:8081/auth';

  isAuthenticated = signal<boolean>(!!this.getAccessToken());

  constructor(private http: HttpClient, private router: Router) {}

  login(email: string, password: string) {
    return this.http.post<AuthTokens>(`${this.API}/login`, { email, password }).pipe(
      tap(tokens => this.saveTokens(tokens))
    );
  }

  register(username: string, email: string, password: string) {
    return this.http.post<string>(`${this.API}/register`, { username, email, password });
  }

  refresh() {
    const refreshToken = this.getRefreshToken();
    return this.http.post<AuthTokens>(`${this.API}/refresh`, { refreshToken }).pipe(
      tap(tokens => this.saveTokens(tokens))
    );
  }

  logout() {
    const refreshToken = this.getRefreshToken();
    this.http.post(`${this.API}/logout`, { refreshToken }).subscribe();
    this.clearTokens();
    this.router.navigate(['/login']);
  }

  getAccessToken(): string | null {
    return localStorage.getItem('access_token');
  }

  getRefreshToken(): string | null {
    return localStorage.getItem('refresh_token');
  }

  private saveTokens(tokens: AuthTokens) {
    localStorage.setItem('access_token', tokens.accessToken);
    localStorage.setItem('refresh_token', tokens.refreshToken);
    this.isAuthenticated.set(true);
  }

  private clearTokens() {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    this.isAuthenticated.set(false);
  }
}
