interface Props {
  value: string;
  onChange: (value: string) => void;
  mode: 'object' | 'array';
}

export default function HeaderEditor({ value, onChange, mode }: Props) {
  const placeholder = mode === 'object'
    ? '{\n  "X-Custom-Header": "value"\n}'
    : '[\n  "X-Header-To-Remove"\n]';

  return (
    <div style={styles.wrapper}>
      <textarea
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        rows={6}
        style={styles.textarea}
        spellCheck={false}
      />
      <div style={styles.hint}>
        {mode === 'object' ? 'Formato: {"Header-Name": "value"}' : 'Formato: ["Header-Name"]'}
        {' — '}
        <button type="button" style={styles.clearBtn}
          onClick={() => onChange(mode === 'object' ? '{\n  \n}' : '[\n  \n]')}>
          limpar
        </button>
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrapper: { position: 'relative' },
  textarea: {
    width: '100%', minHeight: 120,
    padding: '10px 12px', borderRadius: 8,
    border: '1px solid #2a2f48', background: '#0f1119',
    color: '#a5b4fc', fontSize: 12, fontFamily: '"Fira Code", "Cascadia Code", monospace',
    outline: 'none', resize: 'vertical', boxSizing: 'border-box',
    lineHeight: 1.6,
  },
  hint: {
    fontSize: 10, color: '#4b5070', marginTop: 4,
    display: 'flex', justifyContent: 'space-between',
  },
  clearBtn: {
    background: 'none', border: 'none', color: '#6b7094',
    cursor: 'pointer', fontSize: 10, textDecoration: 'underline',
  },
};
