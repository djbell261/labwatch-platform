import { useState } from "react";

function LoginPage({
  onSubmit,
  onShowRegister,
  onContinueToDashboard,
  loading = false,
  error = "",
  notice = "",
  authDisabled = false,
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  return (
    <section className="auth-page">
      <div className="auth-card">
        <div className="auth-kicker">
          LabWatch Auth
        </div>
        <h1 className="auth-title">Sign in</h1>
        <p className="auth-copy">
          {authDisabled
            ? "Authentication is disabled for this local demo environment."
            : "Continue to your machines, telemetry, alerts, and AI monitoring workspace."}
        </p>

        {notice ? <div className="auth-notice">{notice}</div> : null}

        {authDisabled ? (
          <div className="auth-demo-state">
            <div className="auth-demo-message">
              Local mode keeps the operational app open without requiring credentials. You can still preview the auth
              entry experience here.
            </div>
            <button type="button" className="auth-submit-button" onClick={onContinueToDashboard}>
              Continue to Dashboard
            </button>
          </div>
        ) : (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              onSubmit({ email, password });
            }}
            className="auth-form"
          >
            <input
              type="email"
              placeholder="Email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="auth-input"
            />
            <input
              type="password"
              placeholder="Password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="auth-input"
            />
            {error ? <div className="auth-error">{error}</div> : null}
            <button type="submit" disabled={loading} className={`auth-submit-button ${loading ? "is-loading" : ""}`}>
              {loading ? "Signing in..." : "Login"}
            </button>
          </form>
        )}

        <button
          type="button"
          onClick={onShowRegister}
          className="auth-secondary-link"
        >
          Need an account? Sign Up
        </button>
      </div>
    </section>
  );
}

export default LoginPage;
