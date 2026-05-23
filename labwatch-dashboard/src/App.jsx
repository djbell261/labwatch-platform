import { Suspense, lazy, useState } from "react";
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
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import UnauthorizedPage from "./pages/UnauthorizedPage";

const DashboardPage = lazy(() => import("./pages/DashboardPage"));
const IncidentsPage = lazy(() => import("./pages/IncidentsPage"));
const IncidentDetailPage = lazy(() => import("./pages/IncidentDetailPage"));
const AnomaliesPage = lazy(() => import("./pages/AnomaliesPage"));
const AnomalyDetailPage = lazy(() => import("./pages/AnomalyDetailPage"));
const MachinesPage = lazy(() => import("./pages/MachinesPage"));
const MachineDetailPage = lazy(() => import("./pages/MachineDetailPage"));
const AssistantPage = lazy(() => import("./pages/AssistantPage"));

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

function RouteLoadingFallback() {
  return (
    <div className="route-loading-state">
      <div className="route-loading-card">
        <div className="route-loading-badge">Loading</div>
        <h2>Preparing operational view...</h2>
        <p>Loading the requested LabWatch workspace.</p>
      </div>
    </div>
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
            <Route
              path="/dashboard"
              element={
                <Suspense fallback={<RouteLoadingFallback />}>
                  <DashboardPage />
                </Suspense>
              }
            />
            <Route
              path="/incidents"
              element={
                <Suspense fallback={<RouteLoadingFallback />}>
                  <IncidentsPage />
                </Suspense>
              }
            />
            <Route
              path="/incidents/:investigationId"
              element={
                <Suspense fallback={<RouteLoadingFallback />}>
                  <IncidentDetailRoute />
                </Suspense>
              }
            />
            <Route
              path="/anomalies"
              element={
                <Suspense fallback={<RouteLoadingFallback />}>
                  <AnomaliesPage />
                </Suspense>
              }
            />
            <Route
              path="/anomalies/:anomalyId"
              element={
                <Suspense fallback={<RouteLoadingFallback />}>
                  <AnomalyDetailPage />
                </Suspense>
              }
            />
            <Route
              path="/machines"
              element={
                <Suspense fallback={<RouteLoadingFallback />}>
                  <MachinesPage />
                </Suspense>
              }
            />
            <Route
              path="/machines/:machineIdentifier"
              element={
                <Suspense fallback={<RouteLoadingFallback />}>
                  <MachineDetailPage />
                </Suspense>
              }
            />
            <Route
              path="/assistant"
              element={
                <Suspense fallback={<RouteLoadingFallback />}>
                  <AssistantPage />
                </Suspense>
              }
            />
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
