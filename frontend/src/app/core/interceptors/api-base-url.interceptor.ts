import { HttpInterceptorFn } from '@angular/common/http';
import { toApiUrl } from '../config/api-base-url';

const LOCALHOST_API_ORIGIN = 'http://localhost:8080';

export const apiBaseUrlInterceptor: HttpInterceptorFn = (req, next) => {
  let rewrittenUrl = req.url;

  if (rewrittenUrl.startsWith(LOCALHOST_API_ORIGIN)) {
    rewrittenUrl = toApiUrl(rewrittenUrl.substring(LOCALHOST_API_ORIGIN.length));
  } else if (rewrittenUrl.startsWith('/api/') || rewrittenUrl.startsWith('api/')) {
    rewrittenUrl = toApiUrl(rewrittenUrl);
  }

  if (rewrittenUrl !== req.url) {
    req = req.clone({ url: rewrittenUrl });
  }

  return next(req);
};

