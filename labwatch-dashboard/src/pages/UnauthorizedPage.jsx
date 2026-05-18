import { useNavigate } from "react-router-dom";

function UnauthorizedPage() {
  const navigate = useNavigate();

  return (
    <div className="content-page">
      <header className="page-header">
        <div className="page-title-group">
          <div className="eyebrow">Access Control</div>
          <h1 className="page-title">Unauthorized</h1>
          <p className="page-subtitle">
            Your current account does not have permission to access this area.
          </p>
        </div>
      </header>

      <section className="surface-card section-card">
        <div className="snapshot-grid">
          <div className="machine-card-subtle">
            Use an account with the required role or return to the main dashboard.
          </div>
          <div className="page-actions" style={{ justifyContent: "flex-start" }}>
            <button type="button" className="action-button" onClick={() => navigate("/dashboard")}>
              Back to Dashboard
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}

export default UnauthorizedPage;
