import { Pipe, PipeTransform } from '@angular/core';
import { EstadoOrden } from './modelos';

const FORMATO_Q = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Q 1,234.56 (mismo formato que el PDF del backend). */
export function quetzales(valor: number | string | null | undefined): string {
  if (valor === null || valor === undefined || valor === '') return '';
  return 'Q ' + FORMATO_Q.format(Number(valor));
}

@Pipe({ name: 'quetzal' })
export class QuetzalPipe implements PipeTransform {
  transform(valor: number | string | null | undefined): string {
    return quetzales(valor);
  }
}

export const ETIQUETA_ESTADO: Record<EstadoOrden, string> = {
  BORRADOR: 'Borrador',
  PENDIENTE: 'Pendiente de aprobación',
  APROBADA: 'Aprobada',
  RECHAZADA: 'Rechazada',
  CERRADA: 'Cerrada',
  CANCELADA: 'Cancelada',
};

@Pipe({ name: 'estado' })
export class EstadoPipe implements PipeTransform {
  transform(valor: EstadoOrden | null | undefined): string {
    return valor ? ETIQUETA_ESTADO[valor] : '';
  }
}

export const FORMATO_FECHA = 'dd/MM/yyyy HH:mm';
