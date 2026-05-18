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
import PublicOnlyRoute from "./components/PublicOnlyRoute";
import PublicShell from "./components/PublicShell";
import RoleGuard from "./components/RoleGuard";
import { useAuth } from "./context/AuthContext";
import AnomalyDetailPage from "./pages/AnomalyDetailPage";
import AnomaliesPage from "./pages/AnomaliesPage";
import AssistantPage from "./pages/AssistantPage";
import DashboardPage from "./pages/DashboardPage";
import HomePage from "./pages/HomePage";
import IncidentDetailPage from "./pages/IncidentDetailPage";
import IncidentsPage from "./pages/IncidentsPage";
import LoginPage from "./pages/LoginPage";
import MachineDetailPage from "./pages/MachineDetailPage";
import MachinesPage from "./pages/MachinesPage";
import RegisterPage from "./pages/RegisterPage";
import UnauthorizedPage from "./pages/UnauthorizedPage";

function LoginRoute() {
  const location = useLocation();
  const navigate = useNavigate();
  const { authEnabled, isAuthenticated, login, sessionNotice } = useAuth();
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState("");
  const fromPath = location.state?.from?.pathname || "/dashboard";

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
      authDisabled={!authEnabled}
      error={authError}
      loading={authLoading}
      notice={sessionNotice}
      onContinueToDashboard={() => navigate("/dashboard")}
      onShowRegister={() => navigate("/signup")}
      onSubmit={handleLogin}
    />
  );
}

function RegisterRoute() {
  const navigate = useNavigate();
  const { authEnabled, isAuthenticated, register, sessionNotice } = useAuth();
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState("");

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
      authDisabled={!authEnabled}
      error={authError}
      loading={authLoading}
      notice={sessionNotice}
      onContinueToDashboard={() => navigate("/dashboard")}
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
    return (
      <div className="app-boot-splash">
        <div className="app-boot-card">
          <div className="app-boot-badge">LabWatch</div>
          <h1>Restoring session...</h1>
          <p>Preparing your monitoring workspace and auth state.</p>
        </div>
      </div>
    );
  }

  return (
    <Routes>
      <Route element={<PublicShell />}>
        <Route path="/" element={<HomePage />} />
        <Route element={<PublicOnlyRoute />}>
          <Route path="/login" element={<LoginRoute />} />
          <Route path="/signup" element={<RegisterRoute />} />
        </Route>
      </Route>

      <Route path="/register" element={<Navigate replace to="/signup" />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<RoleGuard allowedRoles={["ADMIN", "OPERATOR"]} />}>
          <Route element={<AppShell />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/incidents" element={<IncidentsPage />} />
            <Route path="/incidents/:investigationId" element={<IncidentDetailRoute />} />
            <Route path="/anomalies" element={<AnomaliesPage />} />
            <Route path="/anomalies/:anomalyId" element={<AnomalyDetailPage />} />
            <Route path="/machines" element={<MachinesPage />} />
            <Route path="/machines/:machineIdentifier" element={<MachineDetailPage />} />
            <Route path="/assistant" element={<AssistantPage />} />
            <Route path="/unauthorized" element={<UnauthorizedPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate replace to="/" />} />
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
