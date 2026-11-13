import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AccionOrden, CatalogoItem, Departamento, EntradaBitacora, EstadoOrden, Orden, OrdenRequest, OrdenResumen,
  Pagina, Proveedor, Reporte, ReporteDisponible,
} from './modelos';

export interface FiltroOrdenes {
  estado?: EstadoOrden | null;
  texto?: string | null;
  porAprobar?: boolean;
  pagina: number;
  tamano: number;
}

export interface AccionBody {
  version: number;
  motivoId?: number | null;
  comentario?: string | null;
}

/** Una función por endpoint del backend. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private url = environment.apiUrl;

  // -------------------------------------------------------------- órdenes
  ordenes(f: FiltroOrdenes): Observable<Pagina<OrdenResumen>> {
    let params = new HttpParams().set('pagina', f.pagina).set('tamano', f.tamano);
    if (f.estado) params = params.set('estado', f.estado);
    if (f.texto) params = params.set('texto', f.texto);
    if (f.porAprobar) params = params.set('porAprobar', true);
    return this.http.get<Pagina<OrdenResumen>>(`${this.url}/ordenes`, { params });
  }

  orden(id: number): Observable<Orden> {
    return this.http.get<Orden>(`${this.url}/ordenes/${id}`);
  }

  crearOrden(o: OrdenRequest): Observable<Orden> {
    return this.http.post<Orden>(`${this.url}/ordenes`, o);
  }

  actualizarOrden(id: number, o: OrdenRequest): Observable<Orden> {
    return this.http.put<Orden>(`${this.url}/ordenes/${id}`, o);
  }

  eliminarOrden(id: number, version: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/ordenes/${id}`, { params: { version } });
  }

  accion(id: number, accion: Exclude<AccionOrden, 'EDITAR' | 'ELIMINAR'>, body: AccionBody): Observable<Orden> {
    return this.http.post<Orden>(`${this.url}/ordenes/${id}/${accion.toLowerCase()}`, body);
  }

  // -------------------------------------------------------------- catálogos y maestros
  catalogo(tipo: 'MOTIVO_RECHAZO' | 'MOTIVO_CANCELACION' | 'UNIDAD_MEDIDA'): Observable<CatalogoItem[]> {
    return this.http.get<CatalogoItem[]>(`${this.url}/catalogos/${tipo}`);
  }

  departamentos(todos = false): Observable<Departamento[]> {
    return this.http.get<Departamento[]>(`${this.url}/departamentos`, { params: { todos } });
  }

  guardarDepartamento(d: Partial<Departamento>, id?: number): Observable<Departamento> {
    return id
      ? this.http.put<Departamento>(`${this.url}/departamentos/${id}`, d)
      : this.http.post<Departamento>(`${this.url}/departamentos`, d);
  }

  eliminarDepartamento(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/departamentos/${id}`);
  }

  proveedores(todos = false): Observable<Proveedor[]> {
    return this.http.get<Proveedor[]>(`${this.url}/proveedores`, { params: { todos } });
  }

  guardarProveedor(p: Partial<Proveedor>, id?: number): Observable<Proveedor> {
    return id
      ? this.http.put<Proveedor>(`${this.url}/proveedores/${id}`, p)
      : this.http.post<Proveedor>(`${this.url}/proveedores`, p);
  }

  eliminarProveedor(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/proveedores/${id}`);
  }

  bitacora(pagina: number, tamano: number): Observable<Pagina<EntradaBitacora>> {
    return this.http.get<Pagina<EntradaBitacora>>(`${this.url}/bitacora`, { params: { pagina, tamano } });
  }

  // -------------------------------------------------------------- reportes
  reportesDisponibles(): Observable<ReporteDisponible[]> {
    return this.http.get<ReporteDisponible[]>(`${this.url}/reportes`);
  }

  reporte(codigo: string, desde?: string | null, hasta?: string | null): Observable<Reporte> {
    return this.http.get<Reporte>(`${this.url}/reportes/${codigo}`, { params: this.rango(desde, hasta) });
  }

  /** Como blob: el PDF necesita el header Authorization, no se puede abrir con un <a href> simple. */
  reportePdf(codigo: string, desde?: string | null, hasta?: string | null): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.url}/reportes/${codigo}/pdf`, {
      params: this.rango(desde, hasta), responseType: 'blob', observe: 'response',
    });
  }

  private rango(desde?: string | null, hasta?: string | null): HttpParams {
    let p = new HttpParams();
    if (desde) p = p.set('desde', desde);
    if (hasta) p = p.set('hasta', hasta);
    return p;
  }
}
