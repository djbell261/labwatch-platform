import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

function ProtectedRoute() {
  const { authEnabled, isAuthenticated } = useAuth();
  const location = useLocation();

  if (!authEnabled || isAuthenticated) {
    return <Outlet />;
  }

  return <Navigate replace state={{ from: location }} to="/login" />;
}

export default ProtectedRoute;
