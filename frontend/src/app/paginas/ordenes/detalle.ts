import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Avisos } from '../../core/errores';
import { EstadoPipe, FORMATO_FECHA, quetzales, QuetzalPipe } from '../../core/formato';
import { AccionOrden, Orden } from '../../core/modelos';
import { DatosDialogo, DialogoAccion, ResultadoDialogo } from './dialogo-accion';

type AccionTransicion = Exclude<AccionOrden, 'EDITAR' | 'ELIMINAR'>;

const BOTONES: Record<AccionOrden, { texto: string; icono: string }> = {
  EDITAR: { texto: 'Editar', icono: 'edit' },
  ENVIAR: { texto: 'Enviar a aprobación', icono: 'send' },
  APROBAR: { texto: 'Aprobar', icono: 'check_circle' },
  RECHAZAR: { texto: 'Rechazar', icono: 'cancel' },
  RETIRAR: { texto: 'Retirar', icono: 'undo' },
  REABRIR: { texto: 'Regresar a borrador', icono: 'edit_note' },
  CERRAR: { texto: 'Cerrar', icono: 'task_alt' },
  CANCELAR: { texto: 'Cancelar orden', icono: 'block' },
  ELIMINAR: { texto: 'Eliminar', icono: 'delete' },
};

@Component({
  selector: 'app-detalle-orden',
  imports: [RouterLink, DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule, MatTableModule, QuetzalPipe, EstadoPipe],
  template: `
    <div class="pagina">
      @if (cargando()) { <mat-progress-bar mode="indeterminate" /> }
      @if (orden(); as o) {
        <div class="encabezado">
          <div>
            <a mat-button routerLink="/ordenes"><mat-icon>arrow_back</mat-icon> Órdenes</a>
            <h1>{{ o.numero }} <span class="estado" [class]="o.estado">{{ o.estado | estado }}</span></h1>
            @if (o.vencida) { <span class="vencida">Vencida</span> }
          </div>
          <div class="acciones">
            @for (a of o.acciones; track a) {
              @if (principal(a)) {
                <button mat-flat-button (click)="ejecutar(a)" [disabled]="trabajando()">
                  <mat-icon>{{ botones[a].icono }}</mat-icon> {{ botones[a].texto }}
                </button>
              } @else {
                <button mat-stroked-button [class.boton-peligro]="a === 'RECHAZAR' || a === 'ELIMINAR' || a === 'CANCELAR'"
                        (click)="ejecutar(a)" [disabled]="trabajando()">
                  <mat-icon>{{ botones[a].icono }}</mat-icon> {{ botones[a].texto }}
                </button>
              }
            }
          </div>
        </div>

        <div class="tarjeta">
          <div class="datos">
            <div><span class="muted">Descripción</span><div>{{ o.descripcion }}</div></div>
            <div><span class="muted">Proveedor</span><div>{{ o.proveedor.nombre }}</div></div>
            <div><span class="muted">Departamento</span><div>{{ o.departamento.nombre }}</div></div>
            <div><span class="muted">Creada por</span><div>{{ o.creador.nombre }}</div></div>
            <div><span class="muted">Creada</span><div>{{ o.creadoEn | date: formatoFecha }}</div></div>
            @if (o.fechaEnvio) { <div><span class="muted">Enviada a aprobación</span><div>{{ o.fechaEnvio | date: formatoFecha }}</div></div> }
            @if (o.fechaResolucion) { <div><span class="muted">Resuelta</span><div>{{ o.fechaResolucion | date: formatoFecha }}</div></div> }
            @if (o.fechaCierre) { <div><span class="muted">Cerrada</span><div>{{ o.fechaCierre | date: formatoFecha }}</div></div> }
            @if (o.fechaCancelacion) {
              <div><span class="muted">Cancelada</span><div>{{ o.fechaCancelacion | date: formatoFecha }}
                {{ o.motivoCancelacion ? '- ' + o.motivoCancelacion.nombre : '' }}
                {{ o.comentarioCancelacion ? '- ' + o.comentarioCancelacion : '' }}</div></div>
            }
            <div><span class="muted">Monto total</span><div class="monto">{{ o.montoTotal | quetzal }}</div></div>
          </div>
          @if (o.observaciones) { <p><span class="muted">Observaciones</span><br />{{ o.observaciones }}</p> }
        </div>

        <div class="tarjeta">
          <h3>Detalle</h3>
          <div class="tabla-scroll">
            <table mat-table [dataSource]="o.detalles">
              <ng-container matColumnDef="linea"><th mat-header-cell *matHeaderCellDef>#</th><td mat-cell *matCellDef="let d">{{ d.linea }}</td></ng-container>
              <ng-container matColumnDef="descripcion"><th mat-header-cell *matHeaderCellDef>Descripción</th><td mat-cell *matCellDef="let d">{{ d.descripcion }}</td></ng-container>
              <ng-container matColumnDef="unidad"><th mat-header-cell *matHeaderCellDef>Unidad</th><td mat-cell *matCellDef="let d">{{ d.unidadMedida }}</td></ng-container>
              <ng-container matColumnDef="cantidad"><th mat-header-cell *matHeaderCellDef class="num">Cantidad</th><td mat-cell *matCellDef="let d" class="num">{{ d.cantidad }}</td></ng-container>
              <ng-container matColumnDef="precio"><th mat-header-cell *matHeaderCellDef class="num">Precio unitario</th><td mat-cell *matCellDef="let d" class="num">{{ d.precioUnitario | quetzal }}</td></ng-container>
              <ng-container matColumnDef="subtotal"><th mat-header-cell *matHeaderCellDef class="num">Subtotal</th><td mat-cell *matCellDef="let d" class="num">{{ d.subtotal | quetzal }}</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="columnas"></tr>
              <tr mat-row *matRowDef="let d; columns: columnas"></tr>
            </table>
          </div>
          <div class="total">Total: <strong>{{ o.montoTotal | quetzal }}</strong></div>
        </div>

        <div class="fila">
          <div class="tarjeta">
            <h3>Aprobaciones</h3>
            @for (a of o.aprobaciones; track a.id) {
              <div class="evento" [class.anulada]="!a.vigente">
                <mat-icon [class]="a.decision === 'APROBADA' ? 'ok' : 'no'">{{ a.decision === 'APROBADA' ? 'check_circle' : 'cancel' }}</mat-icon>
                <div>
                  <strong>Nivel {{ a.nivel }} - {{ a.decision === 'APROBADA' ? 'Aprobó' : 'Rechazó' }}</strong> {{ a.aprobador.nombre }}
                  <div class="muted">{{ a.fecha | date: formatoFecha }} | límite {{ a.limiteAprobador | quetzal }} | monto {{ a.montoOrden | quetzal }}</div>
                  @if (a.motivoRechazo) { <div>Motivo: {{ a.motivoRechazo.nombre }}</div> }
                  @if (a.comentario) { <div class="muted">"{{ a.comentario }}"</div> }
                  @if (!a.vigente) { <div class="muted">Anulada</div> }
                </div>
              </div>
            } @empty { <div class="muted">Sin decisiones todavía.</div> }
          </div>

          <div class="tarjeta">
            <h3>Historial de estados</h3>
            @for (h of o.historial; track $index) {
              <div class="evento">
                <mat-icon>schedule</mat-icon>
                <div>
                  <strong>{{ h.estadoAnterior ? (h.estadoAnterior | estado) + ' a ' : '' }}{{ h.estadoNuevo | estado }}</strong>
                  <div class="muted">{{ h.fecha | date: formatoFecha }} | {{ h.usuario.nombre }}</div>
                  @if (h.comentario) { <div class="muted">"{{ h.comentario }}"</div> }
                </div>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    h1 { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 4px 0; }
    h3 { margin: 0 0 12px; font: var(--mat-sys-title-medium); }
    .datos { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
    .monto { font: var(--mat-sys-title-large); color: var(--mat-sys-primary); }
    .total { text-align: right; font: var(--mat-sys-title-medium); margin-top: 12px; }
    .evento { display: flex; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .evento:last-child { border-bottom: 0; }
    .anulada { opacity: .55; }
    .ok { color: #2e7d32; } .no { color: #c62828; }
    .boton-peligro { --mat-button-outlined-label-text-color: var(--mat-sys-error); }
  `,
})
export class DetalleOrden {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private avisos = inject(Avisos);
  private dialog = inject(MatDialog);
  private router = inject(Router);

  id = input.required<string>();
  orden = signal<Orden | null>(null);
  cargando = signal(true);
  trabajando = signal(false);

  readonly botones = BOTONES;
  readonly formatoFecha = FORMATO_FECHA;
  readonly columnas = ['linea', 'descripcion', 'unidad', 'cantidad', 'precio', 'subtotal'];

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.cargar(id));
    });
  }

  /** La acción "natural" de cada estado va destacada. */
  principal(a: AccionOrden): boolean {
    return a === 'ENVIAR' || a === 'APROBAR' || a === 'CERRAR';
  }

  ejecutar(a: AccionOrden): void {
    const o = this.orden();
    if (!o) return;
    if (a === 'EDITAR') {
      this.router.navigate(['/ordenes', o.id, 'editar']);
      return;
    }
    this.dialog.open<DialogoAccion, DatosDialogo, ResultadoDialogo>(DialogoAccion, { data: this.configDialogo(a, o) })
      .afterClosed().subscribe((r) => {
        if (!r) return;
        if (a === 'ELIMINAR') this.eliminar(o);
        else this.transicion(o, a, r);
      });
  }

  private configDialogo(a: AccionOrden, o: Orden): DatosDialogo {
    switch (a) {
      case 'APROBAR': {
        const limite = this.auth.usuario()?.limiteAprobacion ?? 0;
        return {
          titulo: `Aprobar ${o.numero}`,
          mensaje: `Monto: ${quetzales(o.montoTotal)}\nTu límite: ${quetzales(limite)}`,
          boton: 'Aprobar',
        };
      }
      case 'RECHAZAR':
        return { titulo: `Rechazar ${o.numero}`, boton: 'Rechazar', peligro: true, tipoMotivo: 'MOTIVO_RECHAZO', motivoObligatorio: true };
      case 'CANCELAR':
        return {
          titulo: `Cancelar ${o.numero}`, boton: 'Cancelar orden', peligro: true, tipoMotivo: 'MOTIVO_CANCELACION',
          motivoObligatorio: o.estado === 'APROBADA',
        };
      case 'ELIMINAR':
        return { titulo: `Eliminar ${o.numero}`, boton: 'Eliminar', peligro: true, sinComentario: true };
      case 'REABRIR':
        return { titulo: `Regresar ${o.numero} a borrador`, boton: 'Regresar a borrador' };
      case 'ENVIAR':
        return { titulo: `Enviar ${o.numero} a aprobación`, mensaje: `Monto: ${quetzales(o.montoTotal)}`, boton: 'Enviar' };
      default:
        return { titulo: `${BOTONES[a].texto}: ${o.numero}`, boton: BOTONES[a].texto };
    }
  }

  private transicion(o: Orden, a: AccionOrden, r: ResultadoDialogo): void {
    this.trabajando.set(true);
    this.api.accion(o.id, a as AccionTransicion, { version: o.version, motivoId: r.motivoId, comentario: r.comentario })
      .subscribe({
        next: (nueva) => {
          this.orden.set(nueva);
          this.trabajando.set(false);
          this.avisos.ok(`${BOTONES[a].texto}: listo`);
        },
        error: (e) => {
          this.trabajando.set(false);
          this.avisos.error(e);
          if (e.status === 409) this.cargar(o.id); // otro usuario la cambió: mostrar lo actual
        },
      });
  }

  private eliminar(o: Orden): void {
    this.api.eliminarOrden(o.id, o.version).subscribe({
      next: () => {
        this.avisos.ok(`Orden ${o.numero} eliminada`);
        this.router.navigate(['/ordenes']);
      },
      error: (e) => this.avisos.error(e),
    });
  }

  private cargar(id: number): void {
    this.cargando.set(true);
    this.api.orden(id).subscribe({
      next: (o) => {
        this.orden.set(o);
        this.cargando.set(false);
      },
      error: (e) => {
        this.cargando.set(false);
        this.avisos.error(e);
        this.router.navigate(['/ordenes']);
      },
    });
  }
}
