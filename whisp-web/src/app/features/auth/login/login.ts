import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { Auth } from '../../../core/services/auth';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  email = '';
  password = '';
  loading = signal(false);
  error = signal('');

  constructor(private auth: Auth, private router: Router) {}

  onSubmit() {
    if (!this.email || !this.password) {
      this.error.set('Preencha todos os campos');
      return;
    }

    this.loading.set(true);
    this.error.set('');

    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/chat']),
      error: () => {
        this.error.set('Email ou senha inválidos');
        this.loading.set(false);
      },
    });
  }
}
