import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../core/api.service';
import { mensajeError } from '../../core/errores';
import { CatalogoItem } from '../../core/modelos';

export interface DatosDialogo {
  titulo: string;
  mensaje?: string;
  boton: string;
  peligro?: boolean;
  tipoMotivo?: 'MOTIVO_RECHAZO' | 'MOTIVO_CANCELACION';
  motivoObligatorio?: boolean;
  sinComentario?: boolean;
}

export interface ResultadoDialogo {
  motivoId: number | null;
  comentario: string | null;
}

/** Confirmación de una acción sobre la orden; pide motivo y/o comentario cuando aplica. */
@Component({
  selector: 'app-dialogo-accion',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>{{ datos.titulo }}</h2>
    <mat-dialog-content [formGroup]="form">
      @if (datos.mensaje) { <p class="mensaje">{{ datos.mensaje }}</p> }
      @if (datos.tipoMotivo) {
        <mat-form-field appearance="outline" class="ancho">
          <mat-label>Motivo</mat-label>
          <mat-select formControlName="motivoId">
            @if (!datos.motivoObligatorio) { <mat-option [value]="null">Sin motivo</mat-option> }
            @for (m of motivos(); track m.id) { <mat-option [value]="m.id">{{ m.descripcion }}</mat-option> }
          </mat-select>
          <mat-error>{{ err('motivoId') }}</mat-error>
        </mat-form-field>
      }
      @if (!datos.sinComentario) {
        <mat-form-field appearance="outline" class="ancho">
          <mat-label>Comentario</mat-label>
          <textarea matInput formControlName="comentario" rows="3" maxlength="500"></textarea>
          <mat-error>{{ err('comentario') }}</mat-error>
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Volver</button>
      <button mat-flat-button [class.peligro]="datos.peligro" (click)="confirmar()">{{ datos.boton }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    .ancho { width: 100%; }
    .mensaje { white-space: pre-line; }
    mat-dialog-content { min-width: min(440px, 80vw); }
    .peligro { --mat-button-filled-container-color: var(--mat-sys-error); --mat-button-filled-label-text-color: var(--mat-sys-on-error); }
  `,
})
export class DialogoAccion implements OnInit {
  datos = inject<DatosDialogo>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<DialogoAccion, ResultadoDialogo>);
  private api = inject(ApiService);
  private fb = inject(FormBuilder);

  motivos = signal<CatalogoItem[]>([]);
  form = this.fb.group({
    motivoId: this.fb.control<number | null>(null, this.datos.motivoObligatorio ? Validators.required : []),
    comentario: this.fb.control<string | null>(null, Validators.maxLength(500)),
  });

  ngOnInit(): void {
    if (this.datos.tipoMotivo) {
      this.api.catalogo(this.datos.tipoMotivo).subscribe((m) => this.motivos.set(m));
    }
  }

  comentarioObligatorio(): boolean {
    const id = this.form.controls.motivoId.value;
    return this.motivos().find((m) => m.id === id)?.codigo === 'OTRO';
  }

  err(campo: string): string {
    return mensajeError(this.form.get(campo));
  }

  confirmar(): void {
    const comentario = this.form.controls.comentario.value?.trim() || null;
    if (this.comentarioObligatorio() && !comentario) {
      this.form.controls.comentario.setErrors({ servidor: 'Describe el motivo cuando eliges "Otro"' });
      this.form.controls.comentario.markAsTouched();
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.ref.close({ motivoId: this.form.controls.motivoId.value, comentario });
  }
}
