import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Rol } from './modelos';

export const autenticadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.autenticado() || inject(Router).createUrlTree(['/login']);
};

/** Oculta pantallas por rol (comodidad de UI; el backend igual rechaza con 403). */
export const rolGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const roles = (route.data['roles'] ?? []) as Rol[];
  const permitido = roles.some((r) => auth.usuario()?.roles.includes(r));
  return permitido || inject(Router).createUrlTree(['/ordenes']);
};
