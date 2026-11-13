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
import { Departamento } from '../../core/modelos';
import { DialogoAccion } from '../ordenes/dialogo-accion';

@Component({
  selector: 'app-departamentos',
  imports: [MatTableModule, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <div class="pagina">
      <div class="encabezado">
        <h1>Departamentos</h1>
        <button mat-flat-button (click)="editar()"><mat-icon>add</mat-icon> Nuevo departamento</button>
      </div>
      <div class="tarjeta tabla-scroll">
        <table mat-table [dataSource]="filas()">
          <ng-container matColumnDef="codigo"><th mat-header-cell *matHeaderCellDef>Código</th><td mat-cell *matCellDef="let d">{{ d.codigo }}</td></ng-container>
          <ng-container matColumnDef="nombre"><th mat-header-cell *matHeaderCellDef>Nombre</th><td mat-cell *matCellDef="let d">{{ d.nombre }}</td></ng-container>
          <ng-container matColumnDef="activo"><th mat-header-cell *matHeaderCellDef>Activo</th><td mat-cell *matCellDef="let d">{{ d.activo ? 'Sí' : 'No' }}</td></ng-container>
          <ng-container matColumnDef="acciones"><th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let d" class="num">
              <button mat-icon-button (click)="editar(d)" matTooltip="Editar" aria-label="Editar"><mat-icon>edit</mat-icon></button>
              <button mat-icon-button (click)="eliminar(d)" matTooltip="Eliminar" aria-label="Eliminar"><mat-icon>delete</mat-icon></button>
            </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columnas"></tr>
          <tr mat-row *matRowDef="let d; columns: columnas"></tr>
        </table>
      </div>
    </div>
  `,
})
export class Departamentos implements OnInit {
  private api = inject(ApiService);
  private avisos = inject(Avisos);
  private dialog = inject(MatDialog);

  filas = signal<Departamento[]>([]);
  readonly columnas = ['codigo', 'nombre', 'activo', 'acciones'];

  ngOnInit(): void {
    this.cargar();
  }

  editar(d?: Departamento): void {
    this.dialog.open(FormDepartamento, { data: d ?? null, width: '440px' }).afterClosed().subscribe((ok) => ok && this.cargar());
  }

  eliminar(d: Departamento): void {
    this.dialog.open(DialogoAccion, {
      data: { titulo: `Eliminar ${d.nombre}`, boton: 'Eliminar', peligro: true, sinComentario: true },
    }).afterClosed().subscribe((r) => {
      if (!r) return;
      this.api.eliminarDepartamento(d.id).subscribe({
        next: () => { this.avisos.ok('Departamento eliminado'); this.cargar(); },
        error: (e) => this.avisos.error(e),
      });
    });
  }

  private cargar(): void {
    this.api.departamentos(true).subscribe({ next: (d) => this.filas.set(d), error: (e) => this.avisos.error(e) });
  }
}

@Component({
  selector: 'app-form-departamento',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatSlideToggleModule],
  template: `
    <h2 mat-dialog-title>{{ datos ? 'Editar departamento' : 'Nuevo departamento' }}</h2>
    <mat-dialog-content [formGroup]="form" class="campos">
      <mat-form-field appearance="outline"><mat-label>Código</mat-label><input matInput formControlName="codigo" />
        <mat-error>{{ err('codigo') }}</mat-error></mat-form-field>
      <mat-form-field appearance="outline"><mat-label>Nombre</mat-label><input matInput formControlName="nombre" />
        <mat-error>{{ err('nombre') }}</mat-error></mat-form-field>
      <mat-slide-toggle formControlName="activo">Activo</mat-slide-toggle>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancelar</button>
      <button mat-flat-button (click)="guardar()">Guardar</button>
    </mat-dialog-actions>
  `,
  styles: `.campos { display: flex; flex-direction: column; }`,
})
export class FormDepartamento {
  datos = inject<Departamento | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<FormDepartamento>);
  private api = inject(ApiService);
  private avisos = inject(Avisos);
  private fb = inject(FormBuilder);

  form = this.fb.group({
    codigo: [this.datos?.codigo ?? '', [Validators.required, Validators.maxLength(20), Validators.pattern(/^[A-Za-z0-9_-]+$/)]],
    nombre: [this.datos?.nombre ?? '', [Validators.required, Validators.maxLength(100)]],
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
    this.api.guardarDepartamento(this.form.getRawValue() as Partial<Departamento>, this.datos?.id).subscribe({
      next: () => { this.avisos.ok('Departamento guardado'); this.ref.close(true); },
      error: (e) => this.avisos.error(e, aplicarErroresServidor(this.form, errorApi(e))),
    });
  }
}
