import { useEffect, useState } from "react";
import AlertsPanel from "../components/AlertsPanel";
import TelemetryCard from "../components/TelemetryCard";
import { getAlerts, getTelemetrySnapshots } from "../services/api";

function getLatestSnapshot(snapshots) {
  if (!Array.isArray(snapshots) || snapshots.length === 0) {
    return null;
  }

  // Always pick the latest item so the dashboard reflects the newest telemetry.
  return [...snapshots].sort((left, right) => {
    const leftTime = new Date(left.timestamp || left.createdAt || 0).getTime();
    const rightTime = new Date(right.timestamp || right.createdAt || 0).getTime();
    return rightTime - leftTime;
  })[0];
}

function Dashboard() {
  const [latestTelemetry, setLatestTelemetry] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [telemetryLoading, setTelemetryLoading] = useState(true);
  const [alertsLoading, setAlertsLoading] = useState(true);
  const [telemetryError, setTelemetryError] = useState("");
  const [alertsError, setAlertsError] = useState("");

  useEffect(() => {
    let isMounted = true;

    const refreshDashboard = async () => {
      setTelemetryLoading(true);
      setAlertsLoading(true);

      try {
        const [telemetryResponse, alertsResponse] = await Promise.all([
          getTelemetrySnapshots(),
          getAlerts(),
        ]);

        if (!isMounted) {
          return;
        }

        setLatestTelemetry(getLatestSnapshot(telemetryResponse));
        setAlerts(alertsResponse);
        setTelemetryError("");
        setAlertsError("");
      } catch (error) {
        if (!isMounted) {
          return;
        }

        const message =
          error?.response?.data?.message ||
          error?.message ||
          "Unable to load dashboard data.";

        // Keep old data visible while surfacing refresh failures gracefully.
        setTelemetryError("Telemetry refresh failed. Please verify the monitoring API is running.");
        setAlertsError("Alert refresh failed. Please verify the alert engine is running.");
        console.error(message);
      } finally {
        if (isMounted) {
          setTelemetryLoading(false);
          setAlertsLoading(false);
        }
      }
    };

    refreshDashboard();
    const intervalId = window.setInterval(refreshDashboard, 5000);

    return () => {
      isMounted = false;
      window.clearInterval(intervalId);
    };
  }, []);

  return (
    <main
      style={{
        color: "#ffffff",
        margin: "0 auto",
        maxWidth: "1200px",
        minHeight: "100vh",
        padding: "40px 20px 48px",
      }}
    >
      <header
        style={{
          marginBottom: "28px",
        }}
      >
        <div
          style={{
            color: "#38bdf8",
            fontSize: "0.9rem",
            letterSpacing: "0.18em",
            marginBottom: "12px",
            textTransform: "uppercase",
          }}
        >
          Distributed System Monitoring
        </div>
        <h1
          style={{
            fontSize: "clamp(2.4rem, 4vw, 4rem)",
            lineHeight: 1,
            margin: 0,
          }}
        >
          LabWatch Dashboard
        </h1>
        <p
          style={{
            color: "#cbd5e1",
            margin: "14px 0 0",
            maxWidth: "760px",
          }}
        >
          Real-time visibility into machine telemetry and active alert states, refreshed
          automatically every 5 seconds.
        </p>
      </header>

      <div
        style={{
          display: "grid",
          gap: "22px",
        }}
      >
        <TelemetryCard
          telemetry={latestTelemetry}
          loading={telemetryLoading}
          error={telemetryError}
        />
        <AlertsPanel alerts={alerts} loading={alertsLoading} error={alertsError} />
      </div>
    </main>
  );
}

export default Dashboard;
