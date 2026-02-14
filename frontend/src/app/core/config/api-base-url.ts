declare global {
  interface Window {
    __BUTMS_CONFIG__?: {
      apiBaseUrl?: string;
    };
  }
}

function normalizeBaseUrl(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  return value.trim().replace(/\/+$/, '');
}

export function getApiBaseUrl(): string {
  if (typeof window === 'undefined') {
    return 'http://localhost:8080';
  }

  const runtimeConfigUrl = normalizeBaseUrl(window.__BUTMS_CONFIG__?.apiBaseUrl);
  if (runtimeConfigUrl) {
    return runtimeConfigUrl;
  }

  const metaConfig = document
    .querySelector('meta[name="butms-api-base-url"]')
    ?.getAttribute('content');
  const metaConfigUrl = normalizeBaseUrl(metaConfig);
  if (metaConfigUrl) {
    return metaConfigUrl;
  }

  return 'http://localhost:8080';
}

export function toApiUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) {
    return path;
  }

  const base = getApiBaseUrl();
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${base}${normalizedPath}`;
}

