/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AUTH_STORAGE_KEY, getAuthConfig, loginUser, registerUser } from "../services/api";

const AuthContext = createContext(null);

function readStoredAuth() {
  try {
    const rawValue = window.localStorage.getItem(AUTH_STORAGE_KEY);
    if (!rawValue) {
      return { token: "", user: null };
    }

    const parsed = JSON.parse(rawValue);
    return {
      token: typeof parsed?.token === "string" ? parsed.token : "",
      user: parsed?.user ?? null,
    };
  } catch {
    return { token: "", user: null };
  }
}

export function AuthProvider({ children }) {
  const [authReady, setAuthReady] = useState(false);
  const [authEnabled, setAuthEnabled] = useState(false);
  const [storedAuth, setStoredAuth] = useState(() => readStoredAuth());
  const token = storedAuth.token;
  const user = storedAuth.user;

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
    };

    setStoredAuth(nextAuth);

    if (nextAuth.token) {
      window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(nextAuth));
    } else {
      window.localStorage.removeItem(AUTH_STORAGE_KEY);
    }
  }, []);

  const login = useCallback(async (credentials) => {
    const response = await loginUser(credentials);
    persistAuth(response);
    return response;
  }, [persistAuth]);

  const register = useCallback(async (payload) => {
    const response = await registerUser(payload);
    persistAuth(response);
    return response;
  }, [persistAuth]);

  const logout = useCallback(() => {
    persistAuth(null);
  }, [persistAuth]);

  const value = useMemo(
    () => ({
      authReady,
      authEnabled,
      token,
      user,
      isAuthenticated: Boolean(token),
      login,
      register,
      logout,
    }),
    [authEnabled, authReady, token, user, login, register, logout]
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
