import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { aplicarErroresServidor, errorApi, mensajeError } from '../core/errores';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <div class="fondo">
      <form class="caja" [formGroup]="form" (ngSubmit)="entrar()">
        @if (cargando()) { <mat-progress-bar mode="indeterminate" class="barra" /> }
        <div class="marca"><mat-icon>receipt_long</mat-icon> Órdenes de Compra</div>
        <p class="muted">Internos: usuario corporativo. Asesores externos: usuario de la aplicación.</p>

        <mat-form-field appearance="outline">
          <mat-label>Usuario</mat-label>
          <input matInput formControlName="username" autocomplete="username" />
          <mat-error>{{ err('username') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Contraseña</mat-label>
          <input matInput [type]="ver() ? 'text' : 'password'" formControlName="password" autocomplete="current-password" />
          <button mat-icon-button matSuffix type="button" (click)="ver.set(!ver())" [attr.aria-label]="ver() ? 'Ocultar' : 'Mostrar'">
            <mat-icon>{{ ver() ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          <mat-error>{{ err('password') }}</mat-error>
        </mat-form-field>

        @if (error()) { <div class="error" role="alert">{{ error() }}</div> }

        <button mat-flat-button type="submit" [disabled]="cargando()">Ingresar</button>
      </form>
    </div>
  `,
  styles: `
    .fondo { min-height: 100vh; display: grid; place-items: center; padding: 16px; box-sizing: border-box;
      background: linear-gradient(135deg, var(--mat-sys-primary-container), var(--mat-sys-surface-container-low)); }
    .caja { position: relative; overflow: hidden; width: 100%; max-width: 380px; display: flex; flex-direction: column; gap: 4px;
      background: var(--mat-sys-surface); border-radius: 4px; padding: 32px 28px 20px; box-shadow: var(--mat-sys-level3); }
    .barra { position: absolute; top: 0; left: 0; right: 0; }
    .marca { display: flex; align-items: center; gap: 8px; font: var(--mat-sys-title-large); color: var(--mat-sys-primary); }
    .error { background: #ffebee; color: #b71c1c; border-radius: 4px; padding: 8px 12px; margin-bottom: 12px; font-size: 14px; }
  `,
})
export class Login {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  cargando = signal(false);
  error = signal('');
  ver = signal(false);

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(100)]],
    password: ['', [Validators.required, Validators.maxLength(200)]],
  });

  constructor() {
    if (this.auth.autenticado()) this.router.navigate(['/ordenes']);
  }

  err(campo: string): string {
    return mensajeError(this.form.get(campo));
  }

  entrar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.cargando.set(true);
    this.error.set('');
    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => this.router.navigate(['/ordenes']),
      error: (e) => {
        this.cargando.set(false);
        const api = errorApi(e);
        aplicarErroresServidor(this.form, api);
        this.error.set(api.mensaje);
      },
    });
  }
}
