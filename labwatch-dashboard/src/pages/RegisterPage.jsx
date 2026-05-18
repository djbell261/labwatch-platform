import { useState } from "react";

function RegisterPage({
  onSubmit,
  onShowLogin,
  onContinueToDashboard,
  loading = false,
  error = "",
  notice = "",
  authDisabled = false,
}) {
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  return (
    <section className="auth-page">
      <div className="auth-card">
        <div className="auth-kicker">
          LabWatch Auth
        </div>
        <h1 className="auth-title">Create account</h1>
        <p className="auth-copy">
          {authDisabled
            ? "Authentication is disabled for this local demo environment."
            : "Create a lightweight LabWatch account so you can claim machines and separate device ownership later."}
        </p>

        {notice ? <div className="auth-notice">{notice}</div> : null}

        {authDisabled ? (
          <div className="auth-demo-state">
            <div className="auth-demo-message">
              Sign up is not required in local demo mode. Jump straight into the monitoring workspace when you are
              ready.
            </div>
            <button type="button" className="auth-submit-button" onClick={onContinueToDashboard}>
              Continue to Dashboard
            </button>
          </div>
        ) : (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              onSubmit({ displayName, email, password });
            }}
            className="auth-form"
          >
            <input
              type="text"
              placeholder="Display name"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              className="auth-input"
            />
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
              {loading ? "Creating account..." : "Sign Up"}
            </button>
          </form>
        )}

        <button
          type="button"
          onClick={onShowLogin}
          className="auth-secondary-link"
        >
          Already have an account? Login
        </button>
      </div>
    </section>
  );
}

export default RegisterPage;
