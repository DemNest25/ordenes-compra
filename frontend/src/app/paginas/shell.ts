import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { QuetzalPipe } from '../core/formato';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatSidenavModule, MatListModule,
    MatIconModule, MatButtonModule, MatMenuModule, QuetzalPipe],
  template: `
    <mat-toolbar class="barra">
      @if (movil()) {
        <button mat-icon-button (click)="nav.toggle()" aria-label="Menú"><mat-icon>menu</mat-icon></button>
      }
      <mat-icon class="logo">receipt_long</mat-icon>
      <span class="titulo">Órdenes de Compra</span>
      <span class="espacio"></span>
      <button mat-button [matMenuTriggerFor]="menu">
        <mat-icon>account_circle</mat-icon>
        @if (!movil()) { {{ auth.usuario()?.nombreCompleto }} }
      </button>
      <mat-menu #menu="matMenu">
        <div class="perfil">
          <strong>{{ auth.usuario()?.nombreCompleto }}</strong>
          <div class="muted">{{ auth.usuario()?.username }} · {{ auth.usuario()?.interno ? 'Interno' : 'Externo' }}</div>
          <div class="muted">Roles: {{ auth.usuario()?.roles?.join(', ') }}</div>
          @if (auth.usuario()?.limiteAprobacion !== null) {
            <div class="muted">Límite de aprobación: {{ auth.usuario()?.limiteAprobacion | quetzal }}</div>
          }
        </div>
        <button mat-menu-item (click)="auth.logout()"><mat-icon>logout</mat-icon> Cerrar sesión</button>
      </mat-menu>
    </mat-toolbar>

    <mat-sidenav-container class="contenedor">
      <mat-sidenav #nav [mode]="movil() ? 'over' : 'side'" [opened]="!movil()" class="lateral">
        <mat-nav-list (click)="movil() && nav.close()">
          <a mat-list-item routerLink="/ordenes" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: true }">
            <mat-icon matListItemIcon>list_alt</mat-icon> <span matListItemTitle>Órdenes</span>
          </a>
          @if (auth.esAprobador()) {
            <a mat-list-item routerLink="/ordenes" [queryParams]="{ porAprobar: true }">
              <mat-icon matListItemIcon>fact_check</mat-icon> <span matListItemTitle>Por aprobar</span>
            </a>
          }
          <a mat-list-item routerLink="/ordenes/nueva" routerLinkActive="activo">
            <mat-icon matListItemIcon>add_circle</mat-icon> <span matListItemTitle>Nueva orden</span>
          </a>
          <a mat-list-item routerLink="/reportes" routerLinkActive="activo">
            <mat-icon matListItemIcon>bar_chart</mat-icon> <span matListItemTitle>Reportes</span>
          </a>
          @if (auth.esAdmin()) {
            <div mat-subheader>Administración</div>
            <a mat-list-item routerLink="/admin/proveedores" routerLinkActive="activo">
              <mat-icon matListItemIcon>local_shipping</mat-icon> <span matListItemTitle>Proveedores</span>
            </a>
            <a mat-list-item routerLink="/admin/departamentos" routerLinkActive="activo">
              <mat-icon matListItemIcon>apartment</mat-icon> <span matListItemTitle>Departamentos</span>
            </a>
            <a mat-list-item routerLink="/admin/bitacora" routerLinkActive="activo">
              <mat-icon matListItemIcon>history</mat-icon> <span matListItemTitle>Bitácora</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content>
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    :host { display: flex; flex-direction: column; height: 100vh; }
    .barra { background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); gap: 8px; flex: 0 0 auto;
      --mat-text-button-label-text-color: var(--mat-sys-on-primary); --mat-icon-button-icon-color: var(--mat-sys-on-primary); }
    .titulo { font: var(--mat-sys-title-medium); }
    .espacio { flex: 1; }
    .contenedor { flex: 1; }
    .lateral { width: 230px; border-right: 1px solid var(--mat-sys-outline-variant); }
    .activo { background: var(--mat-sys-secondary-container); }
    .perfil { padding: 12px 16px; min-width: 220px; }
  `,
})
export class Shell {
  auth = inject(AuthService);
  private breakpoints = inject(BreakpointObserver);
  movil = toSignal(this.breakpoints.observe('(max-width: 800px)').pipe(map((r) => r.matches)), { initialValue: false });
}
