// Espejo de los DTOs del backend (com.diseconstech.ordenes.dto)

export type Rol = 'ADMIN' | 'APROBADOR' | 'EXTERNO';
export type EstadoOrden = 'BORRADOR' | 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'CERRADA' | 'CANCELADA';
export type AccionOrden =
  | 'EDITAR' | 'ELIMINAR' | 'ENVIAR' | 'RETIRAR' | 'APROBAR' | 'RECHAZAR' | 'REABRIR' | 'CERRAR' | 'CANCELAR';

export interface Usuario {
  id: number;
  username: string;
  nombreCompleto: string;
  email: string;
  interno: boolean;
  roles: Rol[];
  limiteAprobacion: number | null;
  departamentoId: number | null;
  departamento: string | null;
}

export interface LoginResponse {
  token: string;
  tipo: string;
  expira: string;
  usuario: Usuario;
}

export interface ErrorCampo {
  campo: string | null;
  mensaje: string;
}

export interface ErrorApi {
  status: number;
  error: string;
  mensaje: string;
  ruta: string;
  fecha: string;
  errores: ErrorCampo[];
}

export interface Ref {
  id: number;
  nombre: string;
}

export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface OrdenResumen {
  id: number;
  numero: string;
  estado: EstadoOrden;
  descripcion: string;
  creador: Ref;
  departamento: Ref;
  proveedor: Ref;
  moneda: string;
  montoTotal: number;
  requiereSegundaAprobacion: boolean;
  creadoEn: string;
  fechaEnvio: string | null;
  vencida: boolean;
}

export interface Detalle {
  id?: number;
  linea?: number;
  descripcion: string;
  unidadMedida: string;
  cantidad: number;
  precioUnitario: number;
  subtotal?: number;
}

export interface Aprobacion {
  id: number;
  nivel: number;
  decision: 'APROBADA' | 'RECHAZADA';
  aprobador: Ref;
  motivoRechazo: Ref | null;
  comentario: string | null;
  montoOrden: number;
  limiteAprobador: number;
  fecha: string;
  vigente: boolean;
}

export interface Historial {
  estadoAnterior: EstadoOrden | null;
  estadoNuevo: EstadoOrden;
  usuario: Ref;
  fecha: string;
  comentario: string | null;
}

export interface Orden extends OrdenResumen {
  observaciones: string | null;
  actualizadoEn: string | null;
  fechaResolucion: string | null;
  fechaCierre: string | null;
  fechaCancelacion: string | null;
  motivoCancelacion: Ref | null;
  comentarioCancelacion: string | null;
  version: number;
  detalles: Detalle[];
  aprobaciones: Aprobacion[];
  historial: Historial[];
  acciones: AccionOrden[];
}

export interface OrdenRequest {
  departamentoId: number | null;
  proveedorId: number | null;
  descripcion: string;
  observaciones: string | null;
  detalles: Detalle[];
  version?: number;
}

export interface CatalogoItem {
  id: number;
  codigo: string;
  descripcion: string;
}

export interface Departamento {
  id: number;
  codigo: string;
  nombre: string;
  activo: boolean;
}

export interface Proveedor {
  id: number;
  nit: string;
  razonSocial: string;
  nombreComercial: string | null;
  telefono: string | null;
  email: string | null;
  direccion: string | null;
  activo: boolean;
}

export type TipoColumna = 'TEXTO' | 'ENTERO' | 'DECIMAL' | 'MONEDA' | 'FECHA_HORA';

export interface Reporte {
  codigo: string;
  titulo: string;
  alcance: string;
  generadoPor: string;
  generadoEn: string;
  columnas: { titulo: string; tipo: TipoColumna }[];
  filas: unknown[][];
  totales: unknown[] | null;
}

export interface ReporteDisponible {
  codigo: string;
  titulo: string;
}

export interface EntradaBitacora {
  id: number;
  fecha: string;
  username: string;
  accion: string;
  entidad: string;
  entidadId: number | null;
  valoresAnteriores: string | null;
  valoresNuevos: string | null;
  ip: string | null;
}
