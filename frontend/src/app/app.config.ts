import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withHashLocation } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Hash (#/ordenes): el Static Site de Render sirve index.html sin reglas de reescritura
    provideRouter(routes, withHashLocation(), withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
