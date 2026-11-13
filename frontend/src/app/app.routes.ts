import { Routes } from '@angular/router';
import { autenticadoGuard, rolGuard } from './core/guards';

// loadComponent = cada pantalla se descarga solo cuando se visita (bundle inicial más liviano)
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./paginas/login').then((m) => m.Login) },
  {
    path: '',
    canActivate: [autenticadoGuard],
    loadComponent: () => import('./paginas/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'ordenes' },
      { path: 'ordenes', loadComponent: () => import('./paginas/ordenes/lista').then((m) => m.ListaOrdenes) },
      { path: 'ordenes/nueva', loadComponent: () => import('./paginas/ordenes/formulario').then((m) => m.FormularioOrden) },
      { path: 'ordenes/:id', loadComponent: () => import('./paginas/ordenes/detalle').then((m) => m.DetalleOrden) },
      { path: 'ordenes/:id/editar', loadComponent: () => import('./paginas/ordenes/formulario').then((m) => m.FormularioOrden) },
      { path: 'reportes', loadComponent: () => import('./paginas/reportes').then((m) => m.Reportes) },
      {
        path: 'admin',
        canActivate: [rolGuard],
        data: { roles: ['ADMIN'] },
        children: [
          { path: 'proveedores', loadComponent: () => import('./paginas/admin/proveedores').then((m) => m.Proveedores) },
          { path: 'departamentos', loadComponent: () => import('./paginas/admin/departamentos').then((m) => m.Departamentos) },
          { path: 'bitacora', loadComponent: () => import('./paginas/admin/bitacora').then((m) => m.Bitacora) },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
