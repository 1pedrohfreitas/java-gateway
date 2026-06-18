import { Outlet, NavLink } from 'react-router-dom';

export default function Layout() {
  return (
    <div style={styles.wrapper}>
      {/* Sidebar */}
      <aside style={styles.sidebar}>
        <div style={styles.brand}>
          <span style={styles.brandIcon}>⚡</span>
          <span style={styles.brandText}>API Gateway</span>
        </div>
        <nav style={styles.nav}>
          <NavLink
            to="/routes"
            end
            style={({ isActive }) => ({ ...styles.navLink, ...(isActive ? styles.navLinkActive : {}) })}
          >
            🗂️ Rotas
          </NavLink>
          <NavLink
            to="/routes/new"
            style={({ isActive }) => ({ ...styles.navLink, ...(isActive ? styles.navLinkActive : {}) })}
          >
            ➕ Nova Rota
          </NavLink>
        </nav>
      </aside>

      {/* Main content */}
      <main style={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrapper: {
    display: 'flex',
    minHeight: '100vh',
    backgroundColor: '#0f1119',
    color: '#e1e4ed',
  },
  sidebar: {
    width: 260,
    background: 'linear-gradient(180deg, #141726 0%, #1a1d2e 100%)',
    borderRight: '1px solid #242840',
    display: 'flex',
    flexDirection: 'column',
    padding: 0,
  },
  brand: {
    padding: '24px 20px',
    borderBottom: '1px solid #242840',
    display: 'flex',
    alignItems: 'center',
    gap: 12,
  },
  brandIcon: { fontSize: 28 },
  brandText: { fontSize: 18, fontWeight: 700, letterSpacing: -0.5 },
  nav: {
    display: 'flex',
    flexDirection: 'column',
    padding: '12px 12px',
    gap: 4,
  },
  navLink: {
    textDecoration: 'none',
    color: '#8b90a5',
    padding: '10px 14px',
    borderRadius: 8,
    fontSize: 14,
    fontWeight: 500,
    transition: 'all 0.15s',
  },
  navLinkActive: {
    color: '#fff',
    background: '#2a2f48',
  },
  main: {
    flex: 1,
    padding: 32,
    overflowY: 'auto',
  },
};
