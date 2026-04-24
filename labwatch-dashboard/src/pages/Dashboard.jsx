import { useEffect, useState } from "react";
import AlertsPanel from "../components/AlertsPanel";
import TelemetryCard from "../components/TelemetryCard";
import TelemetryTrendChart from "../components/TelemetryTrendChart";
import { getAlerts, getTelemetrySnapshots } from "../services/api";
import { createTelemetrySocket } from "../services/socket";

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

function mergeSnapshotIntoHistory(existingSnapshots, incomingSnapshot) {
  const combinedSnapshots = [incomingSnapshot, ...existingSnapshots];
  const deduplicatedSnapshots = combinedSnapshots.filter((snapshot, index, snapshots) => {
    const snapshotId = snapshot.snapshotId || snapshot.id;
    return snapshots.findIndex((candidate) => (candidate.snapshotId || candidate.id) === snapshotId) === index;
  });

  return deduplicatedSnapshots
    .sort((left, right) => {
      const leftTime = new Date(left.timestamp || left.createdAt || 0).getTime();
      const rightTime = new Date(right.timestamp || right.createdAt || 0).getTime();
      return rightTime - leftTime;
    })
    .slice(0, 50);
}

function Dashboard() {
  const [latestTelemetry, setLatestTelemetry] = useState(null);
  const [telemetryHistory, setTelemetryHistory] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [telemetryLoading, setTelemetryLoading] = useState(true);
  const [alertsLoading, setAlertsLoading] = useState(true);
  const [telemetryError, setTelemetryError] = useState("");
  const [alertsError, setAlertsError] = useState("");
  const [socketStatus, setSocketStatus] = useState("connecting");

  useEffect(() => {
    let isMounted = true;

    const loadInitialTelemetry = async () => {
      setTelemetryLoading(true);

      try {
        const telemetryResponse = await getTelemetrySnapshots();

        if (!isMounted) {
          return;
        }

        const recentTelemetry = telemetryResponse.slice(0, 50);
        setTelemetryHistory(recentTelemetry);
        setLatestTelemetry(getLatestSnapshot(recentTelemetry));
        setTelemetryError("");
      } catch (error) {
        if (!isMounted) {
          return;
        }

        const message =
          error?.response?.data?.message ||
          error?.message ||
          "Unable to load telemetry data.";

        // Keep old data visible while surfacing refresh failures gracefully.
        setTelemetryError("Telemetry refresh failed. Please verify the monitoring API is running.");
        console.error(message);
      } finally {
        if (isMounted) {
          setTelemetryLoading(false);
        }
      }
    };

    loadInitialTelemetry();

    const telemetrySocket = createTelemetrySocket({
      onConnect: () => {
        if (!isMounted) {
          return;
        }

        setSocketStatus("connected");
        setTelemetryError("");
      },
      onDisconnect: () => {
        if (!isMounted) {
          return;
        }

        setSocketStatus("reconnecting");
      },
      onError: () => {
        if (!isMounted) {
          return;
        }

        setSocketStatus("reconnecting");
      },
      onTelemetry: (snapshot) => {
        if (!isMounted) {
          return;
        }

        setTelemetryHistory((existingSnapshots) => {
          const nextHistory = mergeSnapshotIntoHistory(existingSnapshots, snapshot);
          setLatestTelemetry(nextHistory[0] || null);
          return nextHistory;
        });
      },
    });

    telemetrySocket.connect();

    return () => {
      isMounted = false;
      telemetrySocket.disconnect();
    };
  }, []);

  useEffect(() => {
    let isMounted = true;

    const refreshAlerts = async () => {
      setAlertsLoading(true);

      try {
        const alertsResponse = await getAlerts();

        if (!isMounted) {
          return;
        }

        setAlerts(alertsResponse);
        setAlertsError("");
      } catch (error) {
        if (!isMounted) {
          return;
        }

        const message =
          error?.response?.data?.message ||
          error?.message ||
          "Unable to load alert data.";

        setAlertsError("Alert refresh failed. Please verify the alert engine is running.");
        console.error(message);
      } finally {
        if (isMounted) {
          setAlertsLoading(false);
        }
      }
    };

    refreshAlerts();
    const intervalId = window.setInterval(refreshAlerts, 15000);

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
          instantly over WebSockets with REST fallback for initial dashboard load.
        </p>
        <div
          style={{
            color: socketStatus === "connected" ? "#34d399" : "#fbbf24",
            fontSize: "0.95rem",
            marginTop: "12px",
          }}
        >
          Telemetry stream: {socketStatus}
        </div>
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
        <TelemetryTrendChart telemetryHistory={telemetryHistory} alerts={alerts} />
        <AlertsPanel alerts={alerts} loading={alertsLoading} error={alertsError} />
      </div>
    </main>
  );
}

export default Dashboard;
