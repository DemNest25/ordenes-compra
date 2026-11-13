import { Component, inject, input, OnInit, signal } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { aplicarErroresServidor, Avisos, errorApi, mensajeError } from '../../core/errores';
import { QuetzalPipe } from '../../core/formato';
import { CatalogoItem, Departamento, Detalle, Orden, OrdenRequest, Proveedor } from '../../core/modelos';

// Mismas reglas que el backend (@Positive, @Digits fraction=2): el usuario ve el error sin esperar al servidor
function positivo(c: AbstractControl): ValidationErrors | null {
  return c.value === null || c.value === '' || Number(c.value) > 0 ? null : { positivo: true };
}
function dosDecimales(c: AbstractControl): ValidationErrors | null {
  return c.value === null || c.value === '' || /^\d+(\.\d{1,2})?$/.test(String(c.value)) ? null : { decimales: true };
}

@Component({
  selector: 'app-formulario-orden',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, MatProgressBarModule, MatTooltipModule, QuetzalPipe],
  template: `
    <div class="pagina">
      <div class="encabezado">
        <h1>{{ id() ? 'Editar orden ' + (orden()?.numero ?? '') : 'Nueva orden de compra' }}</h1>
      </div>
      @if (cargando()) { <mat-progress-bar mode="indeterminate" /> }

      <form [formGroup]="form" (ngSubmit)="guardar()">
        <div class="tarjeta">
          <h3>Datos generales</h3>
          <div class="fila">
            <mat-form-field appearance="outline">
              <mat-label>Departamento</mat-label>
              <mat-select formControlName="departamentoId">
                @for (d of departamentos(); track d.id) { <mat-option [value]="d.id">{{ d.nombre }}</mat-option> }
              </mat-select>
              <mat-error>{{ err('departamentoId') }}</mat-error>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Proveedor</mat-label>
              <mat-select formControlName="proveedorId">
                @for (p of proveedores(); track p.id) { <mat-option [value]="p.id">{{ p.razonSocial }}</mat-option> }
              </mat-select>
              <mat-error>{{ err('proveedorId') }}</mat-error>
            </mat-form-field>
          </div>
          <mat-form-field appearance="outline" class="ancho">
            <mat-label>Descripción</mat-label>
            <input matInput formControlName="descripcion" maxlength="500" />
            <mat-error>{{ err('descripcion') }}</mat-error>
          </mat-form-field>
          <mat-form-field appearance="outline" class="ancho">
            <mat-label>Observaciones</mat-label>
            <textarea matInput formControlName="observaciones" rows="2"></textarea>
            <mat-error>{{ err('observaciones') }}</mat-error>
          </mat-form-field>
        </div>

        <div class="tarjeta">
          <div class="encabezado"><h3>Detalle</h3>
            <button mat-stroked-button type="button" (click)="agregarLinea()"><mat-icon>add</mat-icon> Agregar línea</button>
          </div>
          @if (form.controls.detalles.errors?.['servidor']) {
            <div class="error-campo">{{ form.controls.detalles.errors?.['servidor'] }}</div>
          }
          <div formArrayName="detalles">
            @for (linea of detalles.controls; track linea; let i = $index) {
              <div class="linea" [formGroupName]="i">
                <span class="n">{{ i + 1 }}</span>
                <mat-form-field appearance="outline" class="desc">
                  <mat-label>Producto / descripción</mat-label>
                  <input matInput formControlName="descripcion" />
                  <mat-error>{{ errLinea(i, 'descripcion') }}</mat-error>
                </mat-form-field>
                <mat-form-field appearance="outline" class="corto">
                  <mat-label>Unidad</mat-label>
                  <mat-select formControlName="unidadMedida">
                    @for (u of unidades(); track u.codigo) { <mat-option [value]="u.codigo">{{ u.descripcion }}</mat-option> }
                  </mat-select>
                  <mat-error>{{ errLinea(i, 'unidadMedida') }}</mat-error>
                </mat-form-field>
                <mat-form-field appearance="outline" class="corto">
                  <mat-label>Cantidad</mat-label>
                  <input matInput type="number" formControlName="cantidad" min="0" step="1" />
                  <mat-error>{{ errLinea(i, 'cantidad') }}</mat-error>
                </mat-form-field>
                <mat-form-field appearance="outline" class="corto">
                  <mat-label>Precio unitario (Q)</mat-label>
                  <input matInput type="number" formControlName="precioUnitario" min="0" step="0.01" />
                  <mat-error>{{ errLinea(i, 'precioUnitario') }}</mat-error>
                </mat-form-field>
                <span class="subtotal">{{ subtotal(i) | quetzal }}</span>
                <button mat-icon-button type="button" (click)="quitarLinea(i)" [disabled]="detalles.length === 1"
                        matTooltip="Quitar línea" aria-label="Quitar línea"><mat-icon>delete</mat-icon></button>
              </div>
            }
          </div>
          <div class="total">Total: <strong>{{ total() | quetzal }}</strong></div>
        </div>

        <div class="acciones fin">
          <a mat-button [routerLink]="id() ? ['/ordenes', id()] : ['/ordenes']">Cancelar</a>
          <button mat-flat-button type="submit" [disabled]="guardando()"><mat-icon>save</mat-icon> Guardar borrador</button>
        </div>
      </form>
    </div>
  `,
  styles: `
    h3 { margin: 0 0 12px; font: var(--mat-sys-title-medium); }
    .ancho { width: 100%; }
    .linea { display: flex; gap: 8px; align-items: flex-start; flex-wrap: wrap; border-bottom: 1px dashed var(--mat-sys-outline-variant); padding-top: 8px; }
    .n { width: 24px; padding-top: 18px; color: var(--mat-sys-on-surface-variant); }
    .desc { flex: 3 1 240px; }
    .corto { flex: 1 1 120px; }
    .subtotal { flex: 0 0 120px; text-align: right; padding-top: 18px; font-weight: 500; }
    .total { text-align: right; font: var(--mat-sys-title-medium); margin-top: 12px; }
    .fin { justify-content: flex-end; }
    .error-campo { color: var(--mat-sys-error); margin-bottom: 8px; }
  `,
})
export class FormularioOrden implements OnInit {
  private fb = inject(FormBuilder);
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private avisos = inject(Avisos);
  private router = inject(Router);

  /** :id de la ruta (vacío = nueva). */
  id = input<string>();
  orden = signal<Orden | null>(null);
  departamentos = signal<Departamento[]>([]);
  proveedores = signal<Proveedor[]>([]);
  unidades = signal<CatalogoItem[]>([]);
  cargando = signal(true);
  guardando = signal(false);

  form = this.fb.group({
    departamentoId: this.fb.control<number | null>(null, Validators.required),
    proveedorId: this.fb.control<number | null>(null, Validators.required),
    descripcion: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(500)]),
    observaciones: this.fb.control<string | null>(null, Validators.maxLength(4000)),
    detalles: this.fb.array<FormGroup>([]),
  });

  get detalles(): FormArray<FormGroup> {
    return this.form.controls.detalles;
  }

  ngOnInit(): void {
    forkJoin({
      departamentos: this.api.departamentos(),
      proveedores: this.api.proveedores(),
      unidades: this.api.catalogo('UNIDAD_MEDIDA'),
    }).subscribe({
      next: (r) => {
        this.departamentos.set(r.departamentos);
        this.proveedores.set(r.proveedores);
        this.unidades.set(r.unidades);
        if (this.id()) {
          this.cargarOrden(Number(this.id()));
        } else {
          this.form.patchValue({ departamentoId: this.auth.usuario()?.departamentoId ?? null });
          this.agregarLinea();
          this.cargando.set(false);
        }
      },
      error: (e) => this.avisos.error(e),
    });
  }

  agregarLinea(d?: Detalle): void {
    this.detalles.push(this.fb.group({
      descripcion: [d?.descripcion ?? '', [Validators.required, Validators.maxLength(300)]],
      unidadMedida: [d?.unidadMedida ?? 'UNIDAD', Validators.required],
      cantidad: [d?.cantidad ?? null, [Validators.required, positivo, dosDecimales]],
      precioUnitario: [d?.precioUnitario ?? null, [Validators.required, Validators.min(0), dosDecimales]],
    }));
  }

  quitarLinea(i: number): void {
    this.detalles.removeAt(i);
  }

  subtotal(i: number): number {
    const v = this.detalles.at(i).value;
    return Math.round((Number(v.cantidad) || 0) * (Number(v.precioUnitario) || 0) * 100) / 100;
  }

  /** Solo informativo: el total que vale es el que calcula el servidor. */
  total(): number {
    return this.detalles.controls.reduce((s, _, i) => s + this.subtotal(i), 0);
  }

  err(campo: string): string {
    return mensajeError(this.form.get(campo));
  }

  errLinea(i: number, campo: string): string {
    return mensajeError(this.detalles.at(i).get(campo));
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.avisos.falla('Revisa los campos marcados en rojo');
      return;
    }
    const v = this.form.getRawValue();
    const req: OrdenRequest = {
      departamentoId: v.departamentoId,
      proveedorId: v.proveedorId,
      descripcion: v.descripcion,
      observaciones: v.observaciones,
      detalles: v.detalles as Detalle[],
      version: this.orden()?.version,
    };
    this.guardando.set(true);
    const llamada = this.id() ? this.api.actualizarOrden(Number(this.id()), req) : this.api.crearOrden(req);
    llamada.subscribe({
      next: (o) => {
        this.avisos.ok(`Orden ${o.numero} guardada`);
        this.router.navigate(['/ordenes', o.id]);
      },
      error: (e) => {
        this.guardando.set(false);
        this.avisos.error(e, aplicarErroresServidor(this.form, errorApi(e)));
      },
    });
  }

  private cargarOrden(id: number): void {
    this.api.orden(id).subscribe({
      next: (o) => {
        if (!o.acciones.includes('EDITAR')) {
          this.avisos.falla('Esta orden ya no se puede modificar');
          this.router.navigate(['/ordenes', id]);
          return;
        }
        this.orden.set(o);
        this.form.patchValue({
          departamentoId: o.departamento.id, proveedorId: o.proveedor.id,
          descripcion: o.descripcion, observaciones: o.observaciones,
        });
        o.detalles.forEach((d) => this.agregarLinea(d));
        this.cargando.set(false);
      },
      error: (e) => {
        this.avisos.error(e);
        this.router.navigate(['/ordenes']);
      },
    });
  }
}
