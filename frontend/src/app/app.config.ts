import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withHashLocation } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';

// Nota: el paginador en español (PaginadorEs) se registra en el Shell, no aquí: importarlo
// en este archivo mete todo el módulo de paginador en el bundle inicial (de 88 KB a 153 KB).
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Hash (#/ordenes): el Static Site de Render sirve index.html sin reglas de reescritura
    provideRouter(routes, withHashLocation(), withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
