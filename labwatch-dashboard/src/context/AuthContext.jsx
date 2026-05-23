/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AUTH_INVALIDATED_EVENT, AUTH_STORAGE_KEY, getAuthConfig, loginUser, registerUser } from "../services/api";

const AuthContext = createContext(null);
const DEMO_AUTH_EXPIRATION_MS = 12 * 60 * 60 * 1000;

function buildDemoAuth(credentials = {}) {
  const normalizedEmail = typeof credentials.email === "string" && credentials.email.trim()
    ? credentials.email.trim().toLowerCase()
    : "demo@labwatch.local";
  const rawDisplayName = normalizedEmail.split("@")[0].replace(/[._-]+/g, " ").trim();
  const displayName = rawDisplayName
    ? rawDisplayName.replace(/\b\w/g, (character) => character.toUpperCase())
    : "Demo Operator";

  return {
    token: "labwatch-demo-session",
    userId: "demo-operator",
    email: normalizedEmail,
    displayName,
    role: "OPERATOR",
    expiresAt: new Date(Date.now() + DEMO_AUTH_EXPIRATION_MS).toISOString(),
    mode: "demo",
  };
}

function readStoredAuth() {
  try {
    const rawValue = window.localStorage.getItem(AUTH_STORAGE_KEY);
    if (!rawValue) {
      return { token: "", user: null, role: "", expiresAt: "", notice: "" };
    }

    const parsed = JSON.parse(rawValue);
    const expiresAt = typeof parsed?.expiresAt === "string" ? parsed.expiresAt : "";
    if (expiresAt) {
      const timestamp = new Date(expiresAt).getTime();
      if (!Number.isNaN(timestamp) && timestamp <= Date.now()) {
        return {
          token: "",
          user: null,
          role: "",
          expiresAt: "",
          notice: "Your session expired. Please sign in again.",
        };
      }
    }

    return {
      token: typeof parsed?.token === "string" ? parsed.token : "",
      user: parsed?.user ?? null,
      role: typeof parsed?.role === "string" ? parsed.role : "",
      expiresAt,
      mode: typeof parsed?.mode === "string" ? parsed.mode : "",
      notice: "",
    };
  } catch {
    return { token: "", user: null, role: "", expiresAt: "", notice: "" };
  }
}

export function AuthProvider({ children }) {
  const initialStoredAuth = readStoredAuth();
  const [authReady, setAuthReady] = useState(false);
  const [authEnabled, setAuthEnabled] = useState(false);
  const [storedAuth, setStoredAuth] = useState(initialStoredAuth);
  const [sessionNotice, setSessionNotice] = useState(initialStoredAuth.notice || "");
  const token = storedAuth.token;
  const user = storedAuth.user;
  const role = storedAuth.role;
  const expiresAt = storedAuth.expiresAt;

  useEffect(() => {
    let cancelled = false;
    getAuthConfig()
      .then((enabled) => {
        if (cancelled) {
          return;
        }
        setAuthEnabled(enabled);
      })
      .catch(() => {
        if (!cancelled) {
          setAuthEnabled(false);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setAuthReady(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const handleInvalidation = (event) => {
      const reason = event?.detail?.reason || "invalid_session";
      setStoredAuth({ token: "", user: null, role: "", expiresAt: "" });
      setSessionNotice(
        reason === "access_denied"
          ? "Access denied for this account. Please sign in with a different role if needed."
          : "Your session expired or is no longer valid. Please sign in again."
      );
      window.localStorage.removeItem(AUTH_STORAGE_KEY);
    };

    window.addEventListener(AUTH_INVALIDATED_EVENT, handleInvalidation);
    return () => {
      window.removeEventListener(AUTH_INVALIDATED_EVENT, handleInvalidation);
    };
  }, []);

  const persistAuth = useCallback((payload) => {
    const nextAuth = {
      token: payload?.token || "",
      user: payload
        ? {
            userId: payload.userId,
            email: payload.email,
            displayName: payload.displayName,
          }
        : null,
      role: payload?.role || "",
      expiresAt: payload?.expiresAt || "",
      mode: payload?.mode || "",
    };

    setStoredAuth(nextAuth);
    setSessionNotice("");

    if (nextAuth.token) {
      window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(nextAuth));
    } else {
      window.localStorage.removeItem(AUTH_STORAGE_KEY);
    }
  }, []);

  const login = useCallback(async (credentials) => {
    if (!authEnabled) {
      const response = buildDemoAuth(credentials);
      persistAuth(response);
      return response;
    }

    const response = await loginUser(credentials);
    persistAuth(response);
    return response;
  }, [authEnabled, persistAuth]);

  const register = useCallback(async (payload) => {
    if (!authEnabled) {
      throw new Error("Registration is disabled in demo mode.");
    }

    const response = await registerUser(payload);
    persistAuth(response);
    return response;
  }, [authEnabled, persistAuth]);

  const logout = useCallback(() => {
    persistAuth(null);
  }, [persistAuth]);

  const clearSessionNotice = useCallback(() => {
    setSessionNotice("");
  }, []);

  const value = useMemo(
    () => ({
      authReady,
      authEnabled,
      expiresAt,
      token,
      user,
      role,
      sessionNotice,
      isDemoSession: storedAuth.mode === "demo",
      isAuthenticated: Boolean(token),
      hasAnyRole: (roles = []) => !authEnabled || roles.includes(role),
      login,
      register,
      logout,
      clearSessionNotice,
    }),
    [authEnabled, authReady, clearSessionNotice, expiresAt, login, logout, register, role, sessionNotice, storedAuth.mode, token, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
