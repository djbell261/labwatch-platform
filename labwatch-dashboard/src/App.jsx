import { useState } from "react";
import { useAuth } from "./context/AuthContext";
import Dashboard from "./pages/Dashboard";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";

function App() {
  const { authReady, authEnabled, isAuthenticated, login, register } = useAuth();
  const [mode, setMode] = useState("login");
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState("");

  if (!authReady) {
    return <div style={{ color: "#ffffff", minHeight: "100vh", display: "grid", placeItems: "center" }}>Loading LabWatch...</div>;
  }

  if (!authEnabled || isAuthenticated) {
    return <Dashboard />;
  }

  const handleLogin = async (credentials) => {
    setAuthLoading(true);
    setAuthError("");
    try {
      await login(credentials);
    } catch (error) {
      setAuthError(error?.response?.data?.message || "Unable to login right now.");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleRegister = async (payload) => {
    setAuthLoading(true);
    setAuthError("");
    try {
      await register(payload);
    } catch (error) {
      setAuthError(error?.response?.data?.message || "Unable to create your account right now.");
    } finally {
      setAuthLoading(false);
    }
  };

  return mode === "register" ? (
    <RegisterPage
      loading={authLoading}
      error={authError}
      onShowLogin={() => {
        setAuthError("");
        setMode("login");
      }}
      onSubmit={handleRegister}
    />
  ) : (
    <LoginPage
      loading={authLoading}
      error={authError}
      onShowRegister={() => {
        setAuthError("");
        setMode("register");
      }}
      onSubmit={handleLogin}
    />
  );
}

export default App;
