import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

function RoleGuard({ allowedRoles = [] }) {
  const { authEnabled, hasAnyRole, isAuthenticated } = useAuth();
  const location = useLocation();

  if (!authEnabled) {
    return <Outlet />;
  }

  if (!isAuthenticated) {
    return <Navigate replace state={{ from: location }} to="/login" />;
  }

  if (allowedRoles.length === 0 || hasAnyRole(allowedRoles)) {
    return <Outlet />;
  }

  return <Navigate replace state={{ from: location }} to="/unauthorized" />;
}

export default RoleGuard;
