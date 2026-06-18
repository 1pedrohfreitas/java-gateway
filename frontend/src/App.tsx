import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import RouteList from './pages/RouteList';
import RouteForm from './pages/RouteForm';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Navigate to="/routes" replace />} />
        <Route path="/routes" element={<RouteList />} />
        <Route path="/routes/new" element={<RouteForm />} />
        <Route path="/routes/:id/edit" element={<RouteForm />} />
      </Route>
    </Routes>
  );
}
