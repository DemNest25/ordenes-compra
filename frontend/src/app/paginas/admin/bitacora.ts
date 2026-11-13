import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { ApiService } from '../../core/api.service';
import { Avisos } from '../../core/errores';
import { FORMATO_FECHA } from '../../core/formato';
import { EntradaBitacora } from '../../core/modelos';

/** Auditoría: quién hizo qué, cuándo, desde qué IP, con valores anterior y nuevo. Solo lectura. */
@Component({
  selector: 'app-bitacora',
  imports: [DatePipe, MatTableModule, MatPaginatorModule],
  template: `
    <div class="pagina">
      <div class="encabezado"><h1>Bitácora de auditoría</h1></div>
      <div class="tarjeta tabla-scroll">
        <table mat-table [dataSource]="filas()">
          <ng-container matColumnDef="fecha"><th mat-header-cell *matHeaderCellDef>Fecha</th>
            <td mat-cell *matCellDef="let b" class="nowrap">{{ b.fecha | date: formatoFecha }}</td></ng-container>
          <ng-container matColumnDef="usuario"><th mat-header-cell *matHeaderCellDef>Usuario</th>
            <td mat-cell *matCellDef="let b">{{ b.username }}<div class="muted">{{ b.ip }}</div></td></ng-container>
          <ng-container matColumnDef="accion"><th mat-header-cell *matHeaderCellDef>Acción</th>
            <td mat-cell *matCellDef="let b"><strong>{{ b.accion }}</strong><div class="muted">{{ b.entidad }} {{ b.entidadId ?? '' }}</div></td></ng-container>
          <ng-container matColumnDef="antes"><th mat-header-cell *matHeaderCellDef>Valor anterior</th>
            <td mat-cell *matCellDef="let b"><pre>{{ bonito(b.valoresAnteriores) }}</pre></td></ng-container>
          <ng-container matColumnDef="despues"><th mat-header-cell *matHeaderCellDef>Valor nuevo</th>
            <td mat-cell *matCellDef="let b"><pre>{{ bonito(b.valoresNuevos) }}</pre></td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columnas"></tr>
          <tr mat-row *matRowDef="let b; columns: columnas"></tr>
        </table>
        <mat-paginator [length]="total()" [pageIndex]="pagina()" [pageSize]="tamano" [pageSizeOptions]="[25, 50, 100]" (page)="cambiar($event)" />
      </div>
    </div>
  `,
  styles: `
    pre { margin: 4px 0; font-size: 11px; max-width: 360px; max-height: 160px; overflow: auto; white-space: pre-wrap; word-break: break-word; }
    .nowrap { white-space: nowrap; }
    td { vertical-align: top; }
  `,
})
export class Bitacora implements OnInit {
  private api = inject(ApiService);
  private avisos = inject(Avisos);

  filas = signal<EntradaBitacora[]>([]);
  total = signal(0);
  pagina = signal(0);
  tamano = 50;
  readonly formatoFecha = FORMATO_FECHA;
  readonly columnas = ['fecha', 'usuario', 'accion', 'antes', 'despues'];

  ngOnInit(): void {
    this.cargar();
  }

  cambiar(e: PageEvent): void {
    this.pagina.set(e.pageIndex);
    this.tamano = e.pageSize;
    this.cargar();
  }

  bonito(json: string | null): string {
    if (!json) return '';
    try {
      return JSON.stringify(JSON.parse(json), null, 1);
    } catch {
      return json;
    }
  }

  private cargar(): void {
    this.api.bitacora(this.pagina(), this.tamano).subscribe({
      next: (p) => { this.filas.set(p.contenido); this.total.set(p.totalElementos); },
      error: (e) => this.avisos.error(e),
    });
  }
}
