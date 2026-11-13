import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginResponse, Rol, Usuario } from './modelos';

const CLAVE = 'oc.sesion';

interface Sesion {
  token: string;
  expira: string;
  usuario: Usuario;
}

/**
 * Sesión del usuario. El token se guarda en localStorage para sobrevivir a recargas.
 * Los roles aquí SOLO deciden qué se muestra; quien autoriza de verdad es el backend.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private sesion = signal<Sesion | null>(this.leer());

  readonly usuario = computed(() => this.sesion()?.usuario ?? null);
  readonly autenticado = computed(() => this.sesion() !== null);
  readonly esAdmin = computed(() => this.tiene('ADMIN'));
  readonly esAprobador = computed(() => this.tiene('APROBADOR'));
  readonly esExterno = computed(() => this.tiene('EXTERNO'));

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, { username, password }).pipe(
      tap((r) => this.guardar({ token: r.token, expira: r.expira, usuario: r.usuario })),
    );
  }

  logout(): void {
    try {
      localStorage.removeItem(CLAVE);
    } catch {
      /* almacenamiento no disponible */
    }
    this.sesion.set(null);
    this.router.navigate(['/login']);
  }

  token(): string | null {
    return this.sesion()?.token ?? null;
  }

  private tiene(rol: Rol): boolean {
    return this.sesion()?.usuario.roles.includes(rol) ?? false;
  }

  private guardar(s: Sesion): void {
    try {
      localStorage.setItem(CLAVE, JSON.stringify(s));
    } catch {
      /* la sesión vive solo en memoria */
    }
    this.sesion.set(s);
  }

  private leer(): Sesion | null {
    try {
      const s = JSON.parse(localStorage.getItem(CLAVE) ?? 'null') as Sesion | null;
      // token vencido = sin sesión (evita un primer request fallido)
      return s && new Date(s.expira).getTime() > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
