import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { AbstractControl, FormArray, FormGroup } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ErrorApi } from './modelos';

/** Extrae el ErrorApi del backend (o arma uno genérico si no hubo respuesta). */
export function errorApi(err: unknown): ErrorApi {
  if (err instanceof HttpErrorResponse && err.error && typeof err.error === 'object' && 'mensaje' in err.error) {
    return err.error as ErrorApi;
  }
  const status = err instanceof HttpErrorResponse ? err.status : 0;
  const mensaje = status === 0
    ? 'No hay conexión con el servidor. Si es la primera carga, espera un minuto (hosting gratuito) e intenta de nuevo.'
    : 'Ocurrió un error inesperado';
  return { status, error: '', mensaje, ruta: '', fecha: '', errores: [] };
}

/**
 * Pinta los errores por campo del servidor en el formulario. Entiende rutas como
 * "detalles[0].cantidad" (FormArray). Devuelve los mensajes que no se pudieron ubicar.
 */
export function aplicarErroresServidor(form: FormGroup, error: ErrorApi): string[] {
  const sinUbicar: string[] = [];
  for (const e of error.errores) {
    const control = e.campo ? buscarControl(form, e.campo) : null;
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), servidor: e.mensaje });
      control.markAsTouched();
    } else {
      sinUbicar.push(e.mensaje);
    }
  }
  return sinUbicar;
}

function buscarControl(form: FormGroup, ruta: string): AbstractControl | null {
  let actual: AbstractControl | null = form;
  for (const parte of ruta.replace(/\[(\d+)\]/g, '.$1').split('.')) {
    if (actual instanceof FormArray) actual = actual.at(Number(parte)) ?? null;
    else if (actual instanceof FormGroup) actual = actual.get(parte);
    else return null;
  }
  return actual;
}

/** Mensaje de error estándar para un control (validación del cliente o del servidor). */
export function mensajeError(control: AbstractControl | null): string {
  const e = control?.errors;
  if (!e) return '';
  if (e['servidor']) return e['servidor'];
  if (e['required']) return 'Este campo es obligatorio';
  if (e['maxlength']) return `Máximo ${e['maxlength'].requiredLength} caracteres`;
  if (e['min']) return `Debe ser mayor o igual a ${e['min'].min}`;
  if (e['positivo']) return 'Debe ser mayor que cero';
  if (e['decimales']) return 'Máximo 2 decimales';
  if (e['email']) return 'Correo inválido';
  if (e['pattern']) return 'Formato inválido';
  return 'Valor inválido';
}

@Injectable({ providedIn: 'root' })
export class Avisos {
  private snack = inject(MatSnackBar);

  ok(mensaje: string): void {
    this.snack.open(mensaje, 'OK', { duration: 3500 });
  }

  /** Error de una llamada HTTP; "extra" = mensajes por campo que no se pudieron ubicar en el formulario. */
  error(err: unknown, extra: string[] = []): void {
    this.falla([errorApi(err).mensaje, ...extra].join(' · '));
  }

  falla(mensaje: string): void {
    this.snack.open(mensaje, 'Cerrar', { duration: 8000, panelClass: 'aviso-error' });
  }
}
