import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

export interface AuthTokens {
  accessToken: string;
}

@Injectable({
  providedIn: 'root',
})
export class Auth {
  private readonly API = 'http://localhost:8081/auth';

  private accessToken = signal<string | null>(null);
  isAuthenticated = signal<boolean>(false);

  constructor(private http: HttpClient, private router: Router) {}

  init(): Observable<AuthTokens> {
    return this.http
      .post<AuthTokens>(`${this.API}/refresh`, {}, { withCredentials: true })
      .pipe(tap(tokens => this.saveTokens(tokens)));
  }

  login(email: string, password: string): Observable<AuthTokens> {
    return this.http
      .post<AuthTokens>(`${this.API}/login`, { email, password }, { withCredentials: true })
      .pipe(tap(tokens => this.saveTokens(tokens)));
  }

  register(username: string, email: string, password: string): Observable<string> {
    return this.http.post<string>(`${this.API}/register`, { username, email, password });
  }

  refresh(): Observable<AuthTokens> {
    return this.http
      .post<AuthTokens>(`${this.API}/refresh`, {}, { withCredentials: true })
      .pipe(tap(tokens => this.saveTokens(tokens)));
  }

  logout(): void {
    this.http
      .post(`${this.API}/logout`, {}, { withCredentials: true })
      .subscribe();
    this.clearTokens();
    this.router.navigate(['/login']);
  }

  getAccessToken(): string | null {
    return this.accessToken();
  }

  private saveTokens(tokens: AuthTokens): void {
    this.accessToken.set(tokens.accessToken);
    this.isAuthenticated.set(true);
  }

  private clearTokens(): void {
    this.accessToken.set(null);
    this.isAuthenticated.set(false);
  }
}
