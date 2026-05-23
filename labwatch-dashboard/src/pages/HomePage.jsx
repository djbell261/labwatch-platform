import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const FEATURE_CARDS = [
  {
    title: "Real-time Telemetry Monitoring",
    description: "Track fleet health, telemetry trends, and operational hotspots from a single monitoring surface.",
  },
  {
    title: "AI-Powered Investigations",
    description: "Move from alert to investigation summary quickly with AI-assisted incident context and recommendations.",
  },
  {
    title: "Anomaly Detection",
    description: "Spot abnormal behavior with statistical anomaly workflows layered alongside threshold-based alerting.",
  },
  {
    title: "Alert Workflows",
    description: "Manage alert lifecycles with realistic operator states and cleaner incident-oriented follow-through.",
  },
  {
    title: "Operational Dashboards",
    description: "Give operators a focused mission-control view without drowning them in giant feeds and noisy widgets.",
  },
];

function HomePage() {
  const navigate = useNavigate();
  const { authEnabled, isAuthenticated, logout } = useAuth();

  const handleGetStarted = () => {
    if (isAuthenticated) {
      navigate("/dashboard");
      return;
    }

    navigate(authEnabled ? "/signup" : "/login");
  };

  const handleOpenDashboard = () => {
    navigate("/dashboard");
  };

  const handleLogout = () => {
    logout();
    navigate("/", { replace: true });
  };

  return (
    <div className="home-page">
      {!authEnabled ? (
        <section className="demo-mode-banner">
          <span className="demo-mode-badge">Local Demo Mode</span>
          <span>Auth is disabled in this local environment. You can open the dashboard directly, or preview login and sign up flows.</span>
        </section>
      ) : null}

      <section className="home-hero">
        <div className="home-hero-copy">
          <span className="home-kicker">LabWatch Platform</span>
          <h1 className="home-title">Operational monitoring with AI-assisted investigation built in.</h1>
          <p className="home-description">
            LabWatch helps teams monitor telemetry, surface anomalies, investigate incidents, and keep operational
            visibility clear without turning the product into a wall of noisy infrastructure widgets.
          </p>
          <div className="home-cta-row">
            <button type="button" className="home-primary-button" onClick={handleGetStarted}>
              Get Started
            </button>
            <button type="button" className="home-secondary-button" onClick={handleOpenDashboard}>
              Open Dashboard
            </button>
          </div>
          <div className="home-capability-row">
            <span>Telemetry monitoring</span>
            <span>AI investigations</span>
            <span>Anomaly detection</span>
            <span>Operational visibility</span>
          </div>
        </div>

        <aside className="home-hero-panel">
          <div className="home-panel-card primary">
            <div className="home-panel-label">Monitoring Signal</div>
            <div className="home-panel-value">Live fleet context</div>
            <p>
              Bring alerts, anomalies, machine health, and investigation workflows into a single operational surface.
            </p>
          </div>
          <div className="home-panel-grid">
            <div className="home-panel-card">
              <div className="home-panel-label">Alert workflow</div>
              <div className="home-panel-mini">Active → Acknowledged → Resolved</div>
            </div>
            <div className="home-panel-card">
              <div className="home-panel-label">AI operations</div>
              <div className="home-panel-mini">Summaries, likely cause, recommended action</div>
            </div>
          </div>
        </aside>
      </section>

      <section className="feature-section">
        <div className="feature-section-heading">
          <span className="home-kicker">Feature Preview</span>
          <h2>A cleaner product surface for real monitoring workflows</h2>
          <p>Built to feel like a focused platform MVP rather than an internal engineering sandbox.</p>
        </div>

        <div className="feature-grid">
          {FEATURE_CARDS.map((feature) => (
            <article key={feature.title} className="feature-card">
              <h3>{feature.title}</h3>
              <p>{feature.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="home-bottom-cta">
        <div>
          <span className="home-kicker">Launch the Platform</span>
          <h2>Open the monitoring workspace when you are ready to operate.</h2>
          <p>Use the public landing page to orient first, then move into the operational app with a cleaner workflow.</p>
        </div>
        <div className="home-cta-row">
          <button type="button" className="home-primary-button" onClick={handleOpenDashboard}>
            Open Dashboard
          </button>
          {!isAuthenticated ? (
            <>
              <button type="button" className="home-secondary-button" onClick={() => navigate("/login")}>
                Login
              </button>
              {authEnabled ? (
                <button type="button" className="home-secondary-button" onClick={() => navigate("/signup")}>
                  Sign Up
                </button>
              ) : null}
            </>
          ) : null}
          {isAuthenticated ? (
            <button type="button" className="home-secondary-button" onClick={handleLogout}>
              Logout
            </button>
          ) : null}
        </div>
      </section>
    </div>
  );
}

export default HomePage;
