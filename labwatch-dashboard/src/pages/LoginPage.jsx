import { useState } from "react";

function LoginPage({
  onSubmit,
  onShowRegister,
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
            ? "Enter the demo workspace through a local authentication flow designed for recruiter and showcase environments."
            : "Continue to your machines, telemetry, alerts, and AI monitoring workspace."}
        </p>

        {notice ? <div className="auth-notice">{notice}</div> : null}
        {authDisabled ? (
          <div className="auth-demo-pill">
            <span className="auth-demo-pill-label">Demo authentication mode</span>
            <span>Local environment</span>
          </div>
        ) : null}

        <form
          onSubmit={(event) => {
            event.preventDefault();
            onSubmit({ email, password });
          }}
          className="auth-form"
        >
          <input
            type="email"
            placeholder={authDisabled ? "demo@labwatch.local" : "Email"}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="auth-input"
          />
          <input
            type="password"
            placeholder={authDisabled ? "Any password works in demo mode" : "Password"}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="auth-input"
          />
          {error ? <div className="auth-error">{error}</div> : null}
          <button type="submit" disabled={loading} className={`auth-submit-button ${loading ? "is-loading" : ""}`}>
            {loading ? "Signing in..." : authDisabled ? "Enter Demo Workspace" : "Login"}
          </button>
        </form>

        {!authDisabled ? (
          <button
            type="button"
            onClick={onShowRegister}
            className="auth-secondary-link"
          >
            Need an account? Sign Up
          </button>
        ) : null}
      </div>
    </section>
  );
}

export default LoginPage;
