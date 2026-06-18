export interface RouteConfig {
  id: number;
  path: string;
  targetUrl: string;
  httpMethods: string;
  jwtRequired: boolean;
  enabled: boolean;
  priority: number;
  stripPrefix: boolean;
  timeoutMs: number;
  addRequestHeaders: string | null;
  removeRequestHeaders: string | null;
  addResponseHeaders: string | null;
  removeResponseHeaders: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RouteFormData {
  path: string;
  targetUrl: string;
  httpMethods: string;
  jwtRequired: boolean;
  enabled: boolean;
  priority: number;
  stripPrefix: boolean;
  timeoutMs: number;
  addRequestHeaders: string;
  removeRequestHeaders: string;
  addResponseHeaders: string;
  removeResponseHeaders: string;
}

export const HTTP_METHODS_OPTIONS = [
  { value: '*', label: 'ALL (*)' },
  { value: 'GET', label: 'GET' },
  { value: 'POST', label: 'POST' },
  { value: 'PUT', label: 'PUT' },
  { value: 'DELETE', label: 'DELETE' },
  { value: 'PATCH', label: 'PATCH' },
  { value: 'GET,POST', label: 'GET + POST' },
  { value: 'GET,POST,PUT', label: 'GET + POST + PUT' },
  { value: 'GET,POST,PUT,DELETE', label: 'GET + POST + PUT + DELETE' },
];
