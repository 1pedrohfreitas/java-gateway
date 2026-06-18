import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import type { RouteConfig } from '../types/route';
import * as api from '../api/client';

export default function RouteList() {
  const [routes, setRoutes] = useState<RouteConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  const loadRoutes = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api.fetchRoutes();
      setRoutes(data);
    } catch {
      toast.error('Erro ao carregar rotas');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadRoutes(); }, [loadRoutes]);

  const handleToggle = async (id: number, enabled: boolean) => {
    try {
      const updated = await api.toggleRoute(id, enabled);
      setRoutes(prev => prev.map(r => r.id === id ? updated : r));
      toast.success(enabled ? 'Rota ativada' : 'Rota desativada');
    } catch {
      toast.error('Erro ao alterar status');
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Remover esta rota?')) return;
    try {
      await api.deleteRoute(id);
      setRoutes(prev => prev.filter(r => r.id !== id));
      toast.success('Rota removida');
    } catch {
      toast.error('Erro ao remover rota');
    }
  };

  const handleRefreshCache = async () => {
    try {
      await api.refreshCache();
      toast.success('Cache recarregado');
    } catch {
      toast.error('Erro ao recarregar cache');
    }
  };

  const filtered = routes.filter(r =>
    r.path.toLowerCase().includes(search.toLowerCase()) ||
    r.targetUrl.toLowerCase().includes(search.toLowerCase())
  );

  if (loading) return <div style={styles.loading}>Carregando...</div>;

  return (
    <div>
      {/* Header */}
      <div style={styles.header}>
        <div>
          <h1 style={styles.title}>Configuração de Rotas</h1>
          <p style={styles.subtitle}>{routes.length} rotas configuradas</p>
        </div>
        <div style={styles.headerActions}>
          <input
            style={styles.search}
            placeholder="Buscar por path ou URL..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <button style={styles.btnSecondary} onClick={handleRefreshCache}>
            🔄 Cache
          </button>
          <Link to="/routes/new" style={styles.btnPrimary}>
            ➕ Nova Rota
          </Link>
        </div>
      </div>

      {/* Table */}
      <div style={styles.tableWrap}>
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>Path</th>
              <th style={styles.th}>Target URL</th>
              <th style={styles.th}>Métodos</th>
              <th style={styles.th}>JWT</th>
              <th style={styles.th}>Status</th>
              <th style={styles.th}>Prioridade</th>
              <th style={styles.th}>Ações</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr>
                <td colSpan={7} style={styles.empty}>
                  {search ? 'Nenhuma rota encontrada.' : (
                    <span>
                      Nenhuma rota configurada.{' '}
                      <Link to="/routes/new" style={styles.link}>Criar primeira rota →</Link>
                    </span>
                  )}
                </td>
              </tr>
            )}
            {filtered.map(route => (
              <tr key={route.id} style={styles.tr}>
                <td style={styles.td}>
                  <code style={styles.pathCode}>{route.path}</code>
                </td>
                <td style={styles.td}>
                  <span style={styles.urlText}>{route.targetUrl}</span>
                </td>
                <td style={styles.td}>
                  <span style={styles.badge}>{route.httpMethods}</span>
                </td>
                <td style={styles.td}>
                  {route.jwtRequired ? (
                    <span style={{ ...styles.badge, ...styles.badgeJwt }}>🔒 JWT</span>
                  ) : (
                    <span style={{ ...styles.badge, ...styles.badgeOpen }}>🌐 Aberto</span>
                  )}
                </td>
                <td style={styles.td}>
                  <button
                    onClick={() => handleToggle(route.id, !route.enabled)}
                    style={{
                      ...styles.toggleBtn,
                      ...(route.enabled ? styles.toggleOn : styles.toggleOff),
                    }}
                  >
                    {route.enabled ? 'Ativo' : 'Inativo'}
                  </button>
                </td>
                <td style={styles.td}>
                  <span style={styles.priority}>{route.priority}</span>
                </td>
                <td style={styles.tdActions}>
                  <Link to={`/routes/${route.id}/edit`} style={styles.actionBtn}>
                    ✏️
                  </Link>
                  <button
                    onClick={() => handleDelete(route.id)}
                    style={{ ...styles.actionBtn, ...styles.actionDanger }}
                  >
                    🗑️
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ---- Styles ----
const styles: Record<string, React.CSSProperties> = {
  header: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
    marginBottom: 24, flexWrap: 'wrap', gap: 16,
  },
  title: { fontSize: 24, fontWeight: 700, margin: 0, color: '#f0f2f8' },
  subtitle: { fontSize: 13, color: '#6b7094', margin: '4px 0 0' },
  headerActions: { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' },
  search: {
    padding: '8px 14px', borderRadius: 8, border: '1px solid #2a2f48',
    background: '#141726', color: '#e1e4ed', fontSize: 13, outline: 'none',
    width: 240,
  },
  btnPrimary: {
    textDecoration: 'none', padding: '9px 18px', borderRadius: 8,
    background: '#6366f1', color: '#fff', fontWeight: 600, fontSize: 13,
    border: 'none', cursor: 'pointer', whiteSpace: 'nowrap',
  },
  btnSecondary: {
    padding: '9px 16px', borderRadius: 8, background: '#1e2135',
    color: '#a5aac0', fontWeight: 600, fontSize: 13, border: '1px solid #2a2f48',
    cursor: 'pointer',
  },
  tableWrap: {
    borderRadius: 12, overflow: 'hidden',
    border: '1px solid #242840', background: '#141726',
  },
  table: { width: '100%', borderCollapse: 'collapse' },
  th: {
    textAlign: 'left', padding: '12px 16px', fontSize: 11,
    fontWeight: 600, textTransform: 'uppercase', color: '#6b7094',
    background: '#1a1d30', borderBottom: '1px solid #242840',
  },
  tr: { borderBottom: '1px solid #1e2135' },
  td: { padding: '12px 16px', fontSize: 13, color: '#c5c9db' },
  tdActions: { padding: '12px 16px', display: 'flex', gap: 6 },
  pathCode: {
    background: '#1e2135', padding: '3px 8px', borderRadius: 4,
    fontSize: 12, color: '#a5b4fc', fontFamily: 'monospace',
  },
  urlText: { fontSize: 12, color: '#8b90a5' },
  badge: {
    padding: '3px 10px', borderRadius: 6, fontSize: 11,
    fontWeight: 600, background: '#1e2135', color: '#a5aac0',
  },
  badgeJwt: { background: '#2d1b3d', color: '#d8b4fe' },
  badgeOpen: { background: '#1b2e1b', color: '#86efac' },
  toggleBtn: {
    padding: '4px 12px', borderRadius: 6, fontSize: 11, fontWeight: 600,
    border: 'none', cursor: 'pointer',
  },
  toggleOn: { background: '#1b3a2c', color: '#4ade80' },
  toggleOff: { background: '#3a1b1b', color: '#f87171' },
  priority: {
    fontFamily: 'monospace', fontSize: 13, color: '#fff',
    background: '#2a2f48', padding: '2px 8px', borderRadius: 4,
  },
  actionBtn: {
    background: 'none', border: 'none', cursor: 'pointer',
    fontSize: 16, padding: '4px 6px', borderRadius: 4,
    textDecoration: 'none',
  },
  actionDanger: { filter: 'grayscale(0.3)' },
  loading: { padding: 40, textAlign: 'center', color: '#6b7094', fontSize: 15 },
  empty: { padding: 40, textAlign: 'center', color: '#6b7094', fontSize: 14 },
  link: { color: '#818cf8', textDecoration: 'none' },
};
