import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

function PublicNavbar() {
  const navigate = useNavigate();
  const { authEnabled, isAuthenticated, logout } = useAuth();

  const handleLogout = () => {
    logout();
    navigate("/", { replace: true });
  };

  const showAuthLinks = !isAuthenticated;

  return (
    <header className="public-navbar-shell">
      <div className="public-navbar">
        <NavLink className="public-brand" to="/">
          <span className="public-brand-badge">LW</span>
          <span className="public-brand-copy">
            <span className="public-brand-title">LabWatch</span>
            <span className="public-brand-subtitle">AI-assisted monitoring platform</span>
          </span>
        </NavLink>

        <nav className="public-nav-links" aria-label="Public navigation">
          <NavLink className="public-nav-link" to="/">
            Home
          </NavLink>

          {showAuthLinks ? (
            <>
              <NavLink className="public-nav-link" to="/login">
                Login
              </NavLink>
              {authEnabled ? (
                <NavLink className="public-nav-button primary" to="/signup">
                  Sign Up
                </NavLink>
              ) : null}
            </>
          ) : null}

          <NavLink className="public-nav-button" to="/dashboard">
            Dashboard
          </NavLink>

          {isAuthenticated ? (
            <button type="button" className="public-nav-button ghost" onClick={handleLogout}>
              Logout
            </button>
          ) : null}
        </nav>
      </div>
    </header>
  );
}

export default PublicNavbar;
