import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Avisos } from '../../core/errores';
import { ETIQUETA_ESTADO, EstadoPipe, FORMATO_FECHA, QuetzalPipe } from '../../core/formato';
import { EstadoOrden, OrdenResumen } from '../../core/modelos';

@Component({
  selector: 'app-lista-ordenes',
  imports: [FormsModule, RouterLink, DatePipe, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatProgressBarModule, QuetzalPipe, EstadoPipe],
  template: `
    <div class="pagina">
      <div class="encabezado">
        <h1>{{ titulo() }}</h1>
        <a mat-flat-button routerLink="/ordenes/nueva"><mat-icon>add</mat-icon> Nueva orden</a>
      </div>

      <div class="tarjeta">
        <div class="fila filtros">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Buscar (número o descripción)</mat-label>
            <input matInput [(ngModel)]="texto" (keyup.enter)="buscar()" />
            <button mat-icon-button matSuffix (click)="buscar()" aria-label="Buscar"><mat-icon>search</mat-icon></button>
          </mat-form-field>
          @if (!soloPorAprobar()) {
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Estado</mat-label>
              <mat-select [(ngModel)]="estado" (selectionChange)="buscar()">
                <mat-option [value]="null">Todos</mat-option>
                @for (e of estados; track e.valor) { <mat-option [value]="e.valor">{{ e.etiqueta }}</mat-option> }
              </mat-select>
            </mat-form-field>
          }
        </div>
      </div>

      <div class="tarjeta sin-padding">
        @if (cargando()) { <mat-progress-bar mode="indeterminate" /> }
        <div class="tabla-scroll">
          <table mat-table [dataSource]="filas()">
            <ng-container matColumnDef="numero">
              <th mat-header-cell *matHeaderCellDef>Número</th>
              <td mat-cell *matCellDef="let o"><strong>{{ o.numero }}</strong></td>
            </ng-container>
            <ng-container matColumnDef="descripcion">
              <th mat-header-cell *matHeaderCellDef>Descripción</th>
              <td mat-cell *matCellDef="let o">{{ o.descripcion }}<div class="muted">{{ o.proveedor.nombre }}</div></td>
            </ng-container>
            <ng-container matColumnDef="creador">
              <th mat-header-cell *matHeaderCellDef>Creada por</th>
              <td mat-cell *matCellDef="let o">{{ o.creador.nombre }}</td>
            </ng-container>
            <ng-container matColumnDef="monto">
              <th mat-header-cell *matHeaderCellDef class="num">Monto</th>
              <td mat-cell *matCellDef="let o" class="num">{{ o.montoTotal | quetzal }}</td>
            </ng-container>
            <ng-container matColumnDef="estado">
              <th mat-header-cell *matHeaderCellDef>Estado</th>
              <td mat-cell *matCellDef="let o">
                <span class="estado" [class]="o.estado">{{ o.estado | estado }}</span>
                @if (o.requiereSegundaAprobacion && o.estado === 'PENDIENTE') { <div class="muted">Falta segundo nivel</div> }
                @if (o.vencida) { <div class="vencida">Vencida</div> }
              </td>
            </ng-container>
            <ng-container matColumnDef="fecha">
              <th mat-header-cell *matHeaderCellDef>Creada</th>
              <td mat-cell *matCellDef="let o" class="muted">{{ o.creadoEn | date: formatoFecha }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columnas()"></tr>
            <tr mat-row *matRowDef="let o; columns: columnas()" class="clic" (click)="abrir(o)"></tr>
          </table>
        </div>
        @if (!cargando() && filas().length === 0) { <div class="vacio">No hay órdenes para mostrar.</div> }
        <mat-paginator [length]="total()" [pageIndex]="pagina()" [pageSize]="tamano()" [pageSizeOptions]="[10, 20, 50]"
                       (page)="cambiarPagina($event)" />
      </div>
    </div>
  `,
  styles: `
    .sin-padding { padding: 0; overflow: hidden; }
    .filtros mat-form-field { max-width: 360px; }
    .clic { cursor: pointer; }
    .clic:hover { background: var(--mat-sys-surface-container); }
  `,
})
export class ListaOrdenes {
  private api = inject(ApiService);
  private avisos = inject(Avisos);
  private router = inject(Router);
  private auth = inject(AuthService);

  /** Query param ?porAprobar=true (bandeja del aprobador). */
  porAprobar = input<string | undefined>();
  soloPorAprobar = computed(() => this.porAprobar() === 'true');
  titulo = computed(() => (this.soloPorAprobar() ? 'Pendientes de aprobación' : 'Órdenes de compra'));

  readonly formatoFecha = FORMATO_FECHA;
  readonly estados = (Object.keys(ETIQUETA_ESTADO) as EstadoOrden[]).map((valor) => ({ valor, etiqueta: ETIQUETA_ESTADO[valor] }));

  texto = '';
  estado: EstadoOrden | null = null;
  filas = signal<OrdenResumen[]>([]);
  total = signal(0);
  pagina = signal(0);
  tamano = signal(20);
  cargando = signal(false);

  // Un externo solo ve lo suyo: la columna "Creada por" no aporta
  columnas = computed(() => {
    const base = ['numero', 'descripcion', 'monto', 'estado', 'fecha'];
    return this.auth.esExterno() ? base : ['numero', 'descripcion', 'creador', 'monto', 'estado', 'fecha'];
  });

  constructor() {
    // recarga al entrar y al cambiar entre "Órdenes" y "Por aprobar"
    // untracked: el effect solo depende de soloPorAprobar, no de las señales que lee cargar()
    effect(() => {
      this.soloPorAprobar();
      untracked(() => {
        this.pagina.set(0);
        this.cargar();
      });
    });
  }

  buscar(): void {
    this.pagina.set(0);
    this.cargar();
  }

  cambiarPagina(e: PageEvent): void {
    this.pagina.set(e.pageIndex);
    this.tamano.set(e.pageSize);
    this.cargar();
  }

  abrir(o: OrdenResumen): void {
    this.router.navigate(['/ordenes', o.id]);
  }

  private cargar(): void {
    this.cargando.set(true);
    this.api.ordenes({
      estado: this.soloPorAprobar() ? null : this.estado,
      texto: this.texto.trim() || null,
      porAprobar: this.soloPorAprobar(),
      pagina: this.pagina(),
      tamano: this.tamano(),
    }).subscribe({
      next: (p) => {
        this.filas.set(p.contenido);
        this.total.set(p.totalElementos);
        this.cargando.set(false);
      },
      error: (e) => {
        this.cargando.set(false);
        this.avisos.error(e);
      },
    });
  }
}
