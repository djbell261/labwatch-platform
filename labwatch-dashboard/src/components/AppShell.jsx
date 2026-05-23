import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getInitials } from "../utils/operations";

const NAV_ITEMS = [
  { to: "/dashboard", label: "Dashboard", subtitle: "Mission control" },
  { to: "/incidents", label: "Incidents", subtitle: "Operational queue" },
  { to: "/anomalies", label: "Anomalies", subtitle: "Detection history" },
  { to: "/machines", label: "Machines", subtitle: "Fleet view" },
  { to: "/assistant", label: "Assistant", subtitle: "AI operations" },
];

function AppShell() {
  const navigate = useNavigate();
  const { authEnabled, logout, user } = useAuth();

  const handleLogout = () => {
    logout();
    navigate("/", { replace: true });
  };

  return (
    <div className="dashboard-shell app-shell">
      <aside className="dashboard-sidebar">
        <div className="brand-block">
          <span className="brand-badge">LabWatch Platform</span>
          <h1 className="brand-title">Monitoring Control Plane</h1>
          <p className="brand-copy">
            A cleaner MVP workspace for operators, incidents, anomalies, machines, and AI-assisted triage.
          </p>
        </div>

        <nav className="sidebar-nav" aria-label="Primary navigation">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `sidebar-link ${isActive ? "is-active" : ""}`}
            >
              <span className="sidebar-link-copy">
                <span className="sidebar-link-title">{item.label}</span>
                <span className="sidebar-link-subtitle">{item.subtitle}</span>
              </span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-profile">
            <div className="profile-row">
              <div className="profile-avatar">{getInitials(user?.displayName || user?.email || "LabWatch")}</div>
              <div className="profile-meta">
                <span className="profile-name">{user?.displayName || "Guest Operator"}</span>
                <span className="profile-email">{user?.email || "Authentication disabled"}</span>
              </div>
            </div>
            {authEnabled && user ? (
              <button type="button" className="ghost-button shell-logout" onClick={handleLogout}>
                Logout
              </button>
            ) : null}
          </div>
        </div>
      </aside>

      <main className="dashboard-main">
        <Outlet />
      </main>
    </div>
  );
}

export default AppShell;
