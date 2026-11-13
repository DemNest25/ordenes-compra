import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../core/api.service';
import { Avisos, errorApi } from '../core/errores';
import { ETIQUETA_ESTADO, FORMATO_FECHA, quetzales } from '../core/formato';
import { EstadoOrden, Reporte, ReporteDisponible, TipoColumna } from '../core/modelos';

/**
 * Un solo componente para todos los reportes: el backend manda columnas tipadas y filas,
 * aquí solo se formatea según el tipo. Cada consulta pide datos frescos (el backend responde no-store).
 */
@Component({
  selector: 'app-reportes',
  imports: [FormsModule, DatePipe, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <div class="pagina">
      <div class="encabezado"><h1>Reportes</h1></div>
      <div class="tarjeta">
        <div class="fila">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Reporte</mat-label>
            <mat-select [(ngModel)]="codigo" (selectionChange)="consultar()">
              @for (r of disponibles(); track r.codigo) { <mat-option [value]="r.codigo">{{ r.titulo }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Creadas desde</mat-label>
            <input matInput type="date" [(ngModel)]="desde" />
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Hasta</mat-label>
            <input matInput type="date" [(ngModel)]="hasta" />
          </mat-form-field>
        </div>
        <div class="acciones botones">
          <button mat-flat-button (click)="consultar()" [disabled]="!codigo || cargando()"><mat-icon>refresh</mat-icon> Consultar</button>
          <button mat-stroked-button (click)="pdf()" [disabled]="!codigo || descargando()"><mat-icon>picture_as_pdf</mat-icon> Exportar PDF</button>
        </div>
        @if (error()) { <div class="error">{{ error() }}</div> }
      </div>

      @if (cargando()) { <mat-progress-bar mode="indeterminate" /> }
      @if (reporte(); as r) {
        <div class="tarjeta">
          <h2>{{ r.titulo }}</h2>
          <div class="muted">{{ r.alcance }} | generado {{ r.generadoEn | date: formatoFecha }} por {{ r.generadoPor }}</div>
          <div class="tabla-scroll">
            <table class="reporte">
              <thead><tr>@for (c of r.columnas; track $index) { <th [class.num]="esNumero(c.tipo)">{{ c.titulo }}</th> }</tr></thead>
              <tbody>
                @for (f of r.filas; track $index) {
                  <tr>@for (c of r.columnas; track $index; let i = $index) { <td [class.num]="esNumero(c.tipo)">{{ celda(f[i], c.tipo) }}</td> }</tr>
                } @empty {
                  <tr><td [attr.colspan]="r.columnas.length" class="vacio">Sin datos para los filtros seleccionados.</td></tr>
                }
              </tbody>
              @if (r.totales && r.filas.length) {
                <tfoot><tr>@for (c of r.columnas; track $index; let i = $index) {
                  <td [class.num]="esNumero(c.tipo)">{{ celda(r.totales[i], typeof r.totales[i] === 'string' ? 'TEXTO' : c.tipo) }}</td>
                }</tr></tfoot>
              }
            </table>
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    h2 { margin: 0 0 4px; font: var(--mat-sys-title-large); }
    .botones { margin-top: 12px; }
    .error { color: var(--mat-sys-error); margin-top: 8px; }
    table.reporte { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 14px; }
    .reporte th { text-align: left; background: var(--mat-sys-surface-container); padding: 8px; font-weight: 500; white-space: nowrap; }
    .reporte td { padding: 6px 8px; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .reporte tfoot td { font-weight: 600; background: var(--mat-sys-surface-container); }
  `,
})
export class Reportes implements OnInit {
  private api = inject(ApiService);
  private avisos = inject(Avisos);

  disponibles = signal<ReporteDisponible[]>([]);
  reporte = signal<Reporte | null>(null);
  cargando = signal(false);
  descargando = signal(false);
  error = signal('');
  codigo = '';
  desde = '';
  hasta = '';
  readonly formatoFecha = FORMATO_FECHA;

  ngOnInit(): void {
    this.api.reportesDisponibles().subscribe({
      next: (r) => {
        this.disponibles.set(r);
        if (r.length) {
          this.codigo = r[0].codigo;
          this.consultar();
        }
      },
      error: (e) => this.avisos.error(e),
    });
  }

  consultar(): void {
    this.cargando.set(true);
    this.error.set('');
    this.api.reporte(this.codigo, this.desde, this.hasta).subscribe({
      next: (r) => {
        this.reporte.set(r);
        this.cargando.set(false);
      },
      error: (e) => {
        this.cargando.set(false);
        this.reporte.set(null);
        this.error.set(errorApi(e).mensaje);
      },
    });
  }

  pdf(): void {
    this.descargando.set(true);
    this.api.reportePdf(this.codigo, this.desde, this.hasta).subscribe({
      next: (resp) => {
        const nombre = /filename="?([^";]+)"?/.exec(resp.headers.get('Content-Disposition') ?? '')?.[1] ?? `reporte-${this.codigo}.pdf`;
        const url = URL.createObjectURL(resp.body!);
        const a = document.createElement('a');
        a.href = url;
        a.download = nombre;
        a.click();
        URL.revokeObjectURL(url);
        this.descargando.set(false);
      },
      error: async (e) => {
        this.descargando.set(false);
        // con responseType blob el error también llega como blob: hay que leerlo para mostrar el mensaje
        if (e.error instanceof Blob) {
          try {
            this.error.set(JSON.parse(await e.error.text()).mensaje);
            return;
          } catch { /* no era JSON */ }
        }
        this.error.set(errorApi(e).mensaje);
      },
    });
  }

  esNumero(tipo: TipoColumna): boolean {
    return tipo === 'MONEDA' || tipo === 'DECIMAL' || tipo === 'ENTERO';
  }

  celda(v: unknown, tipo: TipoColumna): string {
    if (v === null || v === undefined) return '';
    switch (tipo) {
      case 'MONEDA': return quetzales(v as number);
      case 'DECIMAL': return Number(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
      case 'FECHA_HORA': return new Date(v as string).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' });
      default: return ETIQUETA_ESTADO[v as EstadoOrden] ?? String(v);
    }
  }
}
