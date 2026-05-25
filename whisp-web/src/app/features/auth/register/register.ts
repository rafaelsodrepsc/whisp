import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { Auth } from '../../../core/services/auth';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  username = '';
  email = '';
  password = '';
  loading = signal(false);
  error = signal('');

  constructor(private auth: Auth, private router: Router) {}

  onSubmit() {
    if (!this.username || !this.email || !this.password) {
      this.error.set('Preencha todos os campos');
      return;
    }

    this.loading.set(true);
    this.error.set('');

    this.auth.register(this.username, this.email, this.password).subscribe({
      next: () => this.router.navigate(['/login']),
      error: (err) => {
        if (err.status === 409) {
          this.error.set('Email ou username já cadastrado');
        } else {
          this.error.set('Erro ao criar conta. Tente novamente.');
        }
        this.loading.set(false);
      },
    });
  }
}
