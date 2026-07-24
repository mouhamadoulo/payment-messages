import { HttpInterceptorFn } from '@angular/common/http';
import { API_CONFIG } from '../config/api.config';

export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  const isRelative = req.url.startsWith('/') && !req.url.startsWith('//');
  const alreadyPrefixed = req.url.startsWith(API_CONFIG.baseUrl);
  const apiReq = isRelative && !alreadyPrefixed
    ? req.clone({ url: `${API_CONFIG.baseUrl}${req.url}` })
    : req;
  return next(apiReq);
};
