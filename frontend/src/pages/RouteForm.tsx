import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import type { RouteFormData, RouteConfig } from '../types/route';
import { HTTP_METHODS_OPTIONS } from '../types/route';
import * as api from '../api/client';
import HeaderEditor from '../components/HeaderEditor';

const EMPTY_FORM: RouteFormData = {
  path: '',
  targetUrl: '',
  httpMethods: '*',
  jwtRequired: false,
  enabled: true,
  priority: 0,
  stripPrefix: true,
  timeoutMs: 30000,
  addRequestHeaders: '{\n  \n}',
  removeRequestHeaders: '[\n  \n]',
  addResponseHeaders: '{\n  \n}',
  removeResponseHeaders: '[\n  \n]',
};

export default function RouteForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEdit = !!id;

  const [form, setForm] = useState<RouteFormData>(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const loadRoute = useCallback(async () => {
    if (!id) return;
    try {
      setLoading(true);
      const route: RouteConfig = await api.fetchRoute(Number(id));
      setForm({
        path: route.path,
        targetUrl: route.targetUrl,
        httpMethods: route.httpMethods,
        jwtRequired: route.jwtRequired,
        enabled: route.enabled,
        priority: route.priority,
        stripPrefix: route.stripPrefix,
        timeoutMs: route.timeoutMs,
        addRequestHeaders: route.addRequestHeaders || '{\n  \n}',
        removeRequestHeaders: route.removeRequestHeaders || '[\n  \n]',
        addResponseHeaders: route.addResponseHeaders || '{\n  \n}',
        removeResponseHeaders: route.removeResponseHeaders || '[\n  \n]',
      });
    } catch {
      toast.error('Erro ao carregar rota');
      navigate('/routes');
    } finally {
      setLoading(false);
    }
  }, [id, navigate]);

  useEffect(() => { loadRoute(); }, [loadRoute]);

  const validate = (): boolean => {
    const errs: Record<string, string> = {};
    if (!form.path.trim()) errs.path = 'Path é obrigatório';
    if (!form.targetUrl.trim()) errs.targetUrl = 'Target URL é obrigatória';
    if (!form.httpMethods.trim()) errs.httpMethods = 'Método HTTP é obrigatório';
    if (form.timeoutMs < 1000) errs.timeoutMs = 'Timeout mínimo: 1000ms';

    // Validate JSON
    for (const field of ['addRequestHeaders', 'removeRequestHeaders', 'addResponseHeaders', 'removeResponseHeaders']) {
      const val = (form as any)[field];
      if (val && val.trim() && val.trim() !== '{}' && val.trim() !== '[]') {
        try { JSON.parse(val); } catch {
          errs[field] = 'JSON inválido';
        }
      }
    }
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    // Clean empty JSON objects/arrays
    const payload: RouteFormData = { ...form };
    for (const field of ['addRequestHeaders', 'removeRequestHeaders', 'addResponseHeaders', 'removeResponseHeaders']) {
      const val = (payload as any)[field];
      if (!val || !val.trim() || val.trim() === '{}' || val.trim() === '[]') {
        (payload as any)[field] = null;
      }
    }

    try {
      setSaving(true);
      if (isEdit) {
        await api.updateRoute(Number(id), payload);
        toast.success('Rota atualizada');
      } else {
        await api.createRoute(payload);
        toast.success('Rota criada');
      }
      navigate('/routes');
    } catch (err: any) {
      const msg = err?.response?.data?.message || 'Erro ao salvar rota';
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  const update = (field: keyof RouteFormData, value: any) => {
    setForm(prev => ({ ...prev, [field]: value }));
    if (errors[field]) setErrors(prev => { const n = { ...prev }; delete n[field]; return n; });
  };

  if (loading) return <div style={styles.loading}>Carregando...</div>;

  return (
    <div style={styles.container}>
      <div style={styles.topBar}>
        <Link to="/routes" style={styles.backBtn}>← Voltar</Link>
        <h1 style={styles.formTitle}>{isEdit ? 'Editar Rota' : 'Nova Rota'}</h1>
      </div>

      <form onSubmit={handleSubmit} style={styles.form}>
        {/* ---- Seção Básica ---- */}
        <fieldset style={styles.section}>
          <legend style={styles.legend}>🔗 Configuração Básica</legend>
          <div style={styles.row}>
            <div style={styles.field}>
              <label style={styles.label}>Path Pattern *</label>
              <input style={styles.input} value={form.path}
                onChange={e => update('path', e.target.value)}
                placeholder="/api/meu-servico/**" />
              {errors.path && <span style={styles.error}>{errors.path}</span>}
            </div>
            <div style={styles.field}>
              <label style={styles.label}>Target URL *</label>
              <input style={styles.input} value={form.targetUrl}
                onChange={e => update('targetUrl', e.target.value)}
                placeholder="http://backend:8080" />
              {errors.targetUrl && <span style={styles.error}>{errors.targetUrl}</span>}
            </div>
          </div>

          <div style={styles.row}>
            <div style={styles.fieldSm}>
              <label style={styles.label}>Métodos HTTP *</label>
              <select style={styles.select} value={form.httpMethods}
                onChange={e => update('httpMethods', e.target.value)}>
                {HTTP_METHODS_OPTIONS.map(opt => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            </div>
            <div style={styles.fieldSm}>
              <label style={styles.label}>Prioridade</label>
              <input style={styles.input} type="number"
                value={form.priority}
                onChange={e => update('priority', Number(e.target.value))} />
            </div>
            <div style={styles.fieldSm}>
              <label style={styles.label}>Timeout (ms)</label>
              <input style={styles.input} type="number"
                value={form.timeoutMs}
                onChange={e => update('timeoutMs', Number(e.target.value))} />
              {errors.timeoutMs && <span style={styles.error}>{errors.timeoutMs}</span>}
            </div>
          </div>

          <div style={styles.checkRow}>
            <label style={styles.checkLabel}>
              <input type="checkbox" checked={form.enabled}
                onChange={e => update('enabled', e.target.checked)} />
              <span>Rota Ativa</span>
            </label>
            <label style={styles.checkLabel}>
              <input type="checkbox" checked={form.jwtRequired}
                onChange={e => update('jwtRequired', e.target.checked)} />
              <span>🔒 Exige JWT</span>
            </label>
            <label style={styles.checkLabel}>
              <input type="checkbox" checked={form.stripPrefix}
                onChange={e => update('stripPrefix', e.target.checked)} />
              <span>Strip Prefix</span>
            </label>
          </div>
        </fieldset>

        {/* ---- Seção Headers ---- */}
        <fieldset style={styles.section}>
          <legend style={styles.legend}>📋 Filtro de Headers</legend>
          <div style={styles.headerGrid}>
            <div>
              <h4 style={styles.subTitle}>➕ Add Request Headers</h4>
              <HeaderEditor value={form.addRequestHeaders}
                onChange={v => update('addRequestHeaders', v)}
                mode="object" />
              {errors.addRequestHeaders && <span style={styles.error}>{errors.addRequestHeaders}</span>}
            </div>
            <div>
              <h4 style={styles.subTitle}>➖ Remove Request Headers</h4>
              <HeaderEditor value={form.removeRequestHeaders}
                onChange={v => update('removeRequestHeaders', v)}
                mode="array" />
              {errors.removeRequestHeaders && <span style={styles.error}>{errors.removeRequestHeaders}</span>}
            </div>
            <div>
              <h4 style={styles.subTitle}>➕ Add Response Headers</h4>
              <HeaderEditor value={form.addResponseHeaders}
                onChange={v => update('addResponseHeaders', v)}
                mode="object" />
              {errors.addResponseHeaders && <span style={styles.error}>{errors.addResponseHeaders}</span>}
            </div>
            <div>
              <h4 style={styles.subTitle}>➖ Remove Response Headers</h4>
              <HeaderEditor value={form.removeResponseHeaders}
                onChange={v => update('removeResponseHeaders', v)}
                mode="array" />
              {errors.removeResponseHeaders && <span style={styles.error}>{errors.removeResponseHeaders}</span>}
            </div>
          </div>
        </fieldset>

        {/* ---- Submit ---- */}
        <div style={styles.formActions}>
          <Link to="/routes" style={styles.cancelBtn}>Cancelar</Link>
          <button type="submit" disabled={saving} style={styles.saveBtn}>
            {saving ? 'Salvando...' : (isEdit ? '💾 Atualizar Rota' : '✅ Criar Rota')}
          </button>
        </div>
      </form>
    </div>
  );
}

// ---- Styles ----
const styles: Record<string, React.CSSProperties> = {
  container: { maxWidth: 900, margin: '0 auto' },
  topBar: { display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 },
  backBtn: { color: '#818cf8', textDecoration: 'none', fontSize: 14 },
  formTitle: { fontSize: 22, fontWeight: 700, color: '#f0f2f8', margin: 0 },
  form: { display: 'flex', flexDirection: 'column', gap: 24 },
  section: {
    border: '1px solid #242840', borderRadius: 12, padding: 24,
    background: '#141726',
  },
  legend: { fontSize: 15, fontWeight: 600, color: '#c5c9db', padding: '0 8px' },
  row: { display: 'flex', gap: 16, flexWrap: 'wrap' as const, marginBottom: 16 },
  field: { flex: 1, minWidth: 280 },
  fieldSm: { flex: 1, minWidth: 160 },
  label: { display: 'block', fontSize: 12, fontWeight: 600, color: '#8b90a5', marginBottom: 5 },
  input: {
    width: '100%', padding: '9px 12px', borderRadius: 8,
    border: '1px solid #2a2f48', background: '#0f1119', color: '#e1e4ed',
    fontSize: 13, outline: 'none', boxSizing: 'border-box',
  },
  select: {
    width: '100%', padding: '9px 12px', borderRadius: 8,
    border: '1px solid #2a2f48', background: '#0f1119', color: '#e1e4ed',
    fontSize: 13, outline: 'none', boxSizing: 'border-box',
  },
  checkRow: { display: 'flex', gap: 28, flexWrap: 'wrap' as const },
  checkLabel: {
    display: 'flex', alignItems: 'center', gap: 8, fontSize: 13,
    color: '#c5c9db', cursor: 'pointer',
  },
  headerGrid: {
    display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(380px, 1fr))',
    gap: 20,
  },
  subTitle: { fontSize: 12, fontWeight: 600, color: '#6b7094', textTransform: 'uppercase', margin: '0 0 8px' },
  formActions: { display: 'flex', justifyContent: 'flex-end', gap: 12, alignItems: 'center' },
  cancelBtn: {
    textDecoration: 'none', padding: '10px 24px', borderRadius: 8,
    color: '#8b90a5', fontSize: 13, fontWeight: 600,
    border: '1px solid #2a2f48', background: '#1a1d30',
  },
  saveBtn: {
    padding: '10px 28px', borderRadius: 8, border: 'none', cursor: 'pointer',
    background: '#6366f1', color: '#fff', fontWeight: 600, fontSize: 13,
  },
  error: { display: 'block', color: '#f87171', fontSize: 11, marginTop: 4 },
  loading: { padding: 40, textAlign: 'center', color: '#6b7094' },
};
