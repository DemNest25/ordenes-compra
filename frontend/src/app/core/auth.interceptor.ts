import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/** Agrega el JWT a cada llamada a la API y, si el servidor responde 401, cierra la sesión. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const esApi = req.url.startsWith(environment.apiUrl);
  const esLogin = req.url.endsWith('/auth/login');

  const peticion = esApi && token && !esLogin
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(peticion).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && esApi && !esLogin) {
        auth.logout();
      }
      return throwError(() => err);
    }),
  );
};
