import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../core/api.service';
import { aplicarErroresServidor, Avisos, errorApi, mensajeError } from '../../core/errores';
import { Proveedor } from '../../core/modelos';
import { DialogoAccion } from '../ordenes/dialogo-accion';

@Component({
  selector: 'app-proveedores',
  imports: [MatTableModule, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <div class="pagina">
      <div class="encabezado">
        <h1>Proveedores</h1>
        <button mat-flat-button (click)="editar()"><mat-icon>add</mat-icon> Nuevo proveedor</button>
      </div>
      <div class="tarjeta tabla-scroll">
        <table mat-table [dataSource]="filas()">
          <ng-container matColumnDef="nit"><th mat-header-cell *matHeaderCellDef>NIT</th><td mat-cell *matCellDef="let p">{{ p.nit }}</td></ng-container>
          <ng-container matColumnDef="razonSocial"><th mat-header-cell *matHeaderCellDef>Razón social</th>
            <td mat-cell *matCellDef="let p">{{ p.razonSocial }}<div class="muted">{{ p.nombreComercial }}</div></td></ng-container>
          <ng-container matColumnDef="contacto"><th mat-header-cell *matHeaderCellDef>Contacto</th>
            <td mat-cell *matCellDef="let p">{{ p.telefono }}<div class="muted">{{ p.email }}</div></td></ng-container>
          <ng-container matColumnDef="activo"><th mat-header-cell *matHeaderCellDef>Activo</th>
            <td mat-cell *matCellDef="let p">{{ p.activo ? 'Sí' : 'No' }}</td></ng-container>
          <ng-container matColumnDef="acciones"><th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let p" class="num">
              <button mat-icon-button (click)="editar(p)" matTooltip="Editar" aria-label="Editar"><mat-icon>edit</mat-icon></button>
              <button mat-icon-button (click)="eliminar(p)" matTooltip="Eliminar" aria-label="Eliminar"><mat-icon>delete</mat-icon></button>
            </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columnas"></tr>
          <tr mat-row *matRowDef="let p; columns: columnas"></tr>
        </table>
      </div>
    </div>
  `,
})
export class Proveedores implements OnInit {
  private api = inject(ApiService);
  private avisos = inject(Avisos);
  private dialog = inject(MatDialog);

  filas = signal<Proveedor[]>([]);
  readonly columnas = ['nit', 'razonSocial', 'contacto', 'activo', 'acciones'];

  ngOnInit(): void {
    this.cargar();
  }

  editar(p?: Proveedor): void {
    this.dialog.open(FormProveedor, { data: p ?? null, width: '560px' }).afterClosed().subscribe((ok) => ok && this.cargar());
  }

  eliminar(p: Proveedor): void {
    this.dialog.open(DialogoAccion, {
      data: { titulo: `Eliminar ${p.razonSocial}`, boton: 'Eliminar', peligro: true, sinComentario: true },
    }).afterClosed().subscribe((r) => {
      if (!r) return;
      this.api.eliminarProveedor(p.id).subscribe({
        next: () => { this.avisos.ok('Proveedor eliminado'); this.cargar(); },
        error: (e) => this.avisos.error(e),
      });
    });
  }

  private cargar(): void {
    this.api.proveedores(true).subscribe({ next: (p) => this.filas.set(p), error: (e) => this.avisos.error(e) });
  }
}

@Component({
  selector: 'app-form-proveedor',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatSlideToggleModule],
  template: `
    <h2 mat-dialog-title>{{ datos ? 'Editar proveedor' : 'Nuevo proveedor' }}</h2>
    <mat-dialog-content [formGroup]="form" class="campos">
      <mat-form-field appearance="outline"><mat-label>NIT</mat-label><input matInput formControlName="nit" />
        <mat-error>{{ err('nit') }}</mat-error></mat-form-field>
      <mat-form-field appearance="outline"><mat-label>Razón social</mat-label><input matInput formControlName="razonSocial" />
        <mat-error>{{ err('razonSocial') }}</mat-error></mat-form-field>
      <mat-form-field appearance="outline"><mat-label>Nombre comercial</mat-label><input matInput formControlName="nombreComercial" />
        <mat-error>{{ err('nombreComercial') }}</mat-error></mat-form-field>
      <mat-form-field appearance="outline"><mat-label>Teléfono</mat-label><input matInput formControlName="telefono" />
        <mat-error>{{ err('telefono') }}</mat-error></mat-form-field>
      <mat-form-field appearance="outline"><mat-label>Correo</mat-label><input matInput formControlName="email" />
        <mat-error>{{ err('email') }}</mat-error></mat-form-field>
      <mat-form-field appearance="outline"><mat-label>Dirección</mat-label><input matInput formControlName="direccion" />
        <mat-error>{{ err('direccion') }}</mat-error></mat-form-field>
      <mat-slide-toggle formControlName="activo">Activo</mat-slide-toggle>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancelar</button>
      <button mat-flat-button (click)="guardar()">Guardar</button>
    </mat-dialog-actions>
  `,
  styles: `.campos { display: flex; flex-direction: column; }`,
})
export class FormProveedor {
  datos = inject<Proveedor | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<FormProveedor>);
  private api = inject(ApiService);
  private avisos = inject(Avisos);
  private fb = inject(FormBuilder);

  // Mismas reglas que ProveedorRequest en el backend
  form = this.fb.group({
    nit: [this.datos?.nit ?? '', [Validators.required, Validators.maxLength(20), Validators.pattern(/^[0-9]+-?[0-9Kk]$/)]],
    razonSocial: [this.datos?.razonSocial ?? '', [Validators.required, Validators.maxLength(200)]],
    nombreComercial: [this.datos?.nombreComercial ?? '', Validators.maxLength(200)],
    telefono: [this.datos?.telefono ?? '', [Validators.maxLength(20), Validators.pattern(/^[0-9+\- ]*$/)]],
    email: [this.datos?.email ?? '', [Validators.maxLength(150), Validators.email]],
    direccion: [this.datos?.direccion ?? '', Validators.maxLength(300)],
    activo: [this.datos?.activo ?? true],
  });

  err(campo: string): string {
    return mensajeError(this.form.get(campo));
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.api.guardarProveedor(this.form.getRawValue() as Partial<Proveedor>, this.datos?.id).subscribe({
      next: () => { this.avisos.ok('Proveedor guardado'); this.ref.close(true); },
      error: (e) => this.avisos.error(e, aplicarErroresServidor(this.form, errorApi(e))),
    });
  }
}
