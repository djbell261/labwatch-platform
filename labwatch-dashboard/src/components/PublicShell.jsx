import { Outlet } from "react-router-dom";
import PublicNavbar from "./PublicNavbar";

function PublicShell() {
  return (
    <div className="public-shell">
      <PublicNavbar />
      <main className="public-main">
        <Outlet />
      </main>
    </div>
  );
}

export default PublicShell;
