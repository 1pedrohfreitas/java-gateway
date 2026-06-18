import axios from 'axios';
import type { RouteConfig, RouteFormData } from '../types/route';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api/gateway',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

// ---- Routes CRUD ----

export async function fetchRoutes(): Promise<RouteConfig[]> {
  const { data } = await api.get<RouteConfig[]>('/routes');
  return data;
}

export async function fetchRoute(id: number): Promise<RouteConfig> {
  const { data } = await api.get<RouteConfig>(`/routes/${id}`);
  return data;
}

export async function createRoute(form: RouteFormData): Promise<RouteConfig> {
  const { data } = await api.post<RouteConfig>('/routes', form);
  return data;
}

export async function updateRoute(id: number, form: RouteFormData): Promise<RouteConfig> {
  const { data } = await api.put<RouteConfig>(`/routes/${id}`, form);
  return data;
}

export async function deleteRoute(id: number): Promise<void> {
  await api.delete(`/routes/${id}`);
}

export async function toggleRoute(id: number, enabled: boolean): Promise<RouteConfig> {
  const { data } = await api.patch<RouteConfig>(`/routes/${id}/toggle`, { enabled });
  return data;
}

export async function refreshCache(): Promise<void> {
  await api.post('/routes/refresh-cache');
}
