import { useState } from "react";
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
} from "react-router-dom";
import AppShell from "./components/AppShell";
import ProtectedRoute from "./components/ProtectedRoute";
import { useAuth } from "./context/AuthContext";
import AnomalyDetailPage from "./pages/AnomalyDetailPage";
import AnomaliesPage from "./pages/AnomaliesPage";
import AssistantPage from "./pages/AssistantPage";
import DashboardPage from "./pages/DashboardPage";
import IncidentDetailPage from "./pages/IncidentDetailPage";
import IncidentsPage from "./pages/IncidentsPage";
import LoginPage from "./pages/LoginPage";
import MachineDetailPage from "./pages/MachineDetailPage";
import MachinesPage from "./pages/MachinesPage";
import RegisterPage from "./pages/RegisterPage";

function LoginRoute() {
  const location = useLocation();
  const navigate = useNavigate();
  const { authEnabled, isAuthenticated, login } = useAuth();
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState("");
  const fromPath = location.state?.from?.pathname || "/dashboard";

  if (!authEnabled) {
    return <Navigate replace to="/dashboard" />;
  }

  if (isAuthenticated) {
    return <Navigate replace to={fromPath} />;
  }

  const handleLogin = async (credentials) => {
    setAuthLoading(true);
    setAuthError("");
    try {
      await login(credentials);
      navigate(fromPath, { replace: true });
    } catch (error) {
      setAuthError(error?.response?.data?.message || "Unable to login right now.");
    } finally {
      setAuthLoading(false);
    }
  };

  return (
    <LoginPage
      error={authError}
      loading={authLoading}
      onShowRegister={() => navigate("/register")}
      onSubmit={handleLogin}
    />
  );
}

function RegisterRoute() {
  const navigate = useNavigate();
  const { authEnabled, isAuthenticated, register } = useAuth();
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState("");

  if (!authEnabled) {
    return <Navigate replace to="/dashboard" />;
  }

  if (isAuthenticated) {
    return <Navigate replace to="/dashboard" />;
  }

  const handleRegister = async (payload) => {
    setAuthLoading(true);
    setAuthError("");
    try {
      await register(payload);
      navigate("/dashboard", { replace: true });
    } catch (error) {
      setAuthError(error?.response?.data?.message || "Unable to create your account right now.");
    } finally {
      setAuthLoading(false);
    }
  };

  return (
    <RegisterPage
      error={authError}
      loading={authLoading}
      onShowLogin={() => navigate("/login")}
      onSubmit={handleRegister}
    />
  );
}

function IncidentDetailRoute() {
  const navigate = useNavigate();
  const location = useLocation();
  const { investigationId = "" } = useParams();

  return (
    <IncidentDetailPage
      initialIncident={location.state?.incident || null}
      investigationId={decodeURIComponent(investigationId)}
      onBack={() => navigate("/incidents")}
    />
  );
}

function AppRouter() {
  const { authReady } = useAuth();

  if (!authReady) {
    return <div style={{ color: "#ffffff", minHeight: "100vh", display: "grid", placeItems: "center" }}>Loading LabWatch...</div>;
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginRoute />} />
      <Route path="/register" element={<RegisterRoute />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate replace to="/dashboard" />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/incidents" element={<IncidentsPage />} />
          <Route path="/incidents/:investigationId" element={<IncidentDetailRoute />} />
          <Route path="/anomalies" element={<AnomaliesPage />} />
          <Route path="/anomalies/:anomalyId" element={<AnomalyDetailPage />} />
          <Route path="/machines" element={<MachinesPage />} />
          <Route path="/machines/:machineIdentifier" element={<MachineDetailPage />} />
          <Route path="/assistant" element={<AssistantPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate replace to="/dashboard" />} />
    </Routes>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AppRouter />
    </BrowserRouter>
  );
}

export default App;
