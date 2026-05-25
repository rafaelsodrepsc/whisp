import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Auth } from '../services/auth';
import { catchError, switchMap, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const token = auth.getAccessToken();

  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      const isAuthRoute = req.url.includes('/auth/');
      console.log('Interceptor error:', error.status, 'isAuthRoute:', isAuthRoute, 'url:', req.url);

      if ((error.status === 401 || error.status === 403) && !isAuthRoute) {
        console.log('Attempting refresh...');
        return auth.refresh().pipe(
          switchMap(() => {
            console.log('Refresh successful');
            const newToken = auth.getAccessToken();
            const retryReq = req.clone({
              setHeaders: { Authorization: `Bearer ${newToken}` }
            });
            return next(retryReq);
          }),
          catchError(refreshError => {
            console.log('Refresh failed:', refreshError);
            auth.logout();
            return throwError(() => refreshError);
          })
        );
      }

      return throwError(() => error);
    })
  );
};
