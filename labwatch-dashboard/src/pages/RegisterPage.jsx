import { useState } from "react";

function RegisterPage({ onSubmit, onShowLogin, loading = false, error = "" }) {
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  return (
    <main
      style={{
        alignItems: "center",
        color: "#ffffff",
        display: "flex",
        justifyContent: "center",
        minHeight: "100vh",
        padding: "24px",
      }}
    >
      <section
        style={{
          background: "rgba(15, 23, 42, 0.86)",
          border: "1px solid rgba(148, 163, 184, 0.16)",
          borderRadius: "28px",
          boxShadow: "0 24px 60px rgba(2, 6, 23, 0.4)",
          maxWidth: "420px",
          padding: "28px",
          width: "100%",
        }}
      >
        <div style={{ color: "#38bdf8", fontSize: "0.85rem", fontWeight: 700, letterSpacing: "0.12em", textTransform: "uppercase" }}>
          LabWatch Auth
        </div>
        <h1 style={{ fontSize: "2rem", marginBottom: "10px" }}>Create account</h1>
        <p style={{ color: "#94a3b8", lineHeight: 1.6, marginBottom: "18px" }}>
          Create a lightweight LabWatch account so you can claim machines and separate device ownership later.
        </p>

        <form
          onSubmit={(event) => {
            event.preventDefault();
            onSubmit({ displayName, email, password });
          }}
          style={{ display: "grid", gap: "14px" }}
        >
          <input
            type="text"
            placeholder="Display name"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            style={inputStyle}
          />
          <input
            type="email"
            placeholder="Email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            style={inputStyle}
          />
          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            style={inputStyle}
          />
          {error ? <div style={{ color: "#fca5a5", fontSize: "0.92rem" }}>{error}</div> : null}
          <button type="submit" disabled={loading} style={primaryButtonStyle(loading)}>
            {loading ? "Creating account..." : "Register"}
          </button>
        </form>

        <button
          type="button"
          onClick={onShowLogin}
          style={{
            background: "transparent",
            border: "none",
            color: "#c4b5fd",
            cursor: "pointer",
            marginTop: "16px",
            padding: 0,
          }}
        >
          Already have an account? Login
        </button>
      </section>
    </main>
  );
}

const inputStyle = {
  background: "rgba(15, 23, 42, 0.9)",
  border: "1px solid rgba(148, 163, 184, 0.18)",
  borderRadius: "14px",
  color: "#e2e8f0",
  outline: "none",
  padding: "12px 14px",
};

function primaryButtonStyle(loading) {
  return {
    background: loading ? "rgba(71, 85, 105, 0.7)" : "linear-gradient(135deg, #38bdf8, #8b5cf6)",
    border: "none",
    borderRadius: "14px",
    color: "#ffffff",
    cursor: loading ? "not-allowed" : "pointer",
    fontWeight: 700,
    padding: "12px 14px",
  };
}

export default RegisterPage;
