import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../context/AuthContext";
import {
  claimMachine,
  getAlerts,
  getAnomalies,
  getAvailableMachines,
  getInsight,
  getMachines,
  getRecentInvestigations,
  getTelemetrySnapshots,
  unclaimMachine,
} from "../services/api";
import { createTelemetrySocket } from "../services/socket";
import { getLatestSnapshot, mergeSnapshotIntoHistory } from "../utils/operations";

export function useOperationsData(scopeMachineIdentifier = "") {
  const { authEnabled } = useAuth();
  const [machines, setMachines] = useState([]);
  const [availableMachines, setAvailableMachines] = useState([]);
  const [telemetryHistory, setTelemetryHistory] = useState([]);
  const [latestTelemetry, setLatestTelemetry] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [anomalies, setAnomalies] = useState([]);
  const [recentInvestigations, setRecentInvestigations] = useState([]);
  const [insightSource, setInsightSource] = useState("backend");
  const [socketStatus, setSocketStatus] = useState("connecting");
  const [machinesLoading, setMachinesLoading] = useState(true);
  const [telemetryLoading, setTelemetryLoading] = useState(true);
  const [alertsLoading, setAlertsLoading] = useState(true);
  const [anomaliesLoading, setAnomaliesLoading] = useState(true);
  const [investigationsLoading, setInvestigationsLoading] = useState(true);
  const [insightLoading, setInsightLoading] = useState(true);
  const [machinesError, setMachinesError] = useState("");
  const [telemetryError, setTelemetryError] = useState("");
  const [alertsError, setAlertsError] = useState("");
  const [anomaliesError, setAnomaliesError] = useState("");
  const [investigationsError, setInvestigationsError] = useState("");
  const [insightError, setInsightError] = useState("");
  const [claimError, setClaimError] = useState("");

  const refreshMachines = useCallback(async (isMountedRef) => {
    setMachinesLoading(true);
    try {
      const response = await getMachines();
      if (isMountedRef.current) {
        setMachines(response);
        setMachinesError("");
      }
    } catch (error) {
      if (isMountedRef.current) {
        setMachinesError("Machine inventory is unavailable.");
        console.error(error?.message || "Unable to load machines.");
      }
    } finally {
      if (isMountedRef.current) {
        setMachinesLoading(false);
      }
    }
  }, []);

  const refreshAvailableMachines = useCallback(async (isMountedRef) => {
    if (!authEnabled) {
      if (isMountedRef.current) {
        setAvailableMachines([]);
      }
      return;
    }

    try {
      const response = await getAvailableMachines();
      if (isMountedRef.current) {
        setAvailableMachines(response);
      }
    } catch (error) {
      if (isMountedRef.current) {
        console.error(error?.message || "Unable to load available machines.");
      }
    }
  }, [authEnabled]);

  const refreshTelemetry = useCallback(async (isMountedRef, machineIdentifier = scopeMachineIdentifier) => {
    setTelemetryLoading(true);
    try {
      const response = await getTelemetrySnapshots(machineIdentifier);
      if (!isMountedRef.current) {
        return;
      }
      const recentTelemetry = response.slice(0, 50);
      setTelemetryHistory(recentTelemetry);
      setLatestTelemetry(getLatestSnapshot(recentTelemetry));
      setTelemetryError("");
    } catch (error) {
      if (isMountedRef.current) {
        setTelemetryError("Telemetry stream is unavailable.");
        console.error(error?.message || "Unable to load telemetry data.");
      }
    } finally {
      if (isMountedRef.current) {
        setTelemetryLoading(false);
      }
    }
  }, [scopeMachineIdentifier]);

  const refreshAlerts = useCallback(async (isMountedRef, machineIdentifier = scopeMachineIdentifier) => {
    setAlertsLoading(true);
    try {
      const response = await getAlerts(machineIdentifier);
      if (isMountedRef.current) {
        setAlerts(response);
        setAlertsError("");
      }
    } catch (error) {
      if (isMountedRef.current) {
        setAlertsError("Alert service is unavailable.");
        console.error(error?.message || "Unable to load alerts.");
      }
    } finally {
      if (isMountedRef.current) {
        setAlertsLoading(false);
      }
    }
  }, [scopeMachineIdentifier]);

  const refreshAnomalies = useCallback(async (isMountedRef, machineIdentifier = scopeMachineIdentifier) => {
    setAnomaliesLoading(true);
    try {
      const response = await getAnomalies(machineIdentifier);
      if (isMountedRef.current) {
        setAnomalies(response);
        setAnomaliesError("");
      }
    } catch (error) {
      if (isMountedRef.current) {
        setAnomaliesError("AI anomaly service is unavailable.");
        console.error(error?.message || "Unable to load anomalies.");
      }
    } finally {
      if (isMountedRef.current) {
        setAnomaliesLoading(false);
      }
    }
  }, [scopeMachineIdentifier]);

  const refreshInvestigations = useCallback(async (isMountedRef, machineIdentifier = scopeMachineIdentifier) => {
    setInvestigationsLoading(true);
    try {
      const response = await getRecentInvestigations(machineIdentifier);
      if (isMountedRef.current) {
        setRecentInvestigations(response);
        setInvestigationsError("");
      }
    } catch (error) {
      if (isMountedRef.current) {
        setInvestigationsError("Unable to load recent incidents.");
        console.error(error?.message || "Unable to load investigations.");
      }
    } finally {
      if (isMountedRef.current) {
        setInvestigationsLoading(false);
      }
    }
  }, [scopeMachineIdentifier]);

  const refreshInsight = useCallback(async (isMountedRef, machineIdentifier = scopeMachineIdentifier) => {
    setInsightLoading(true);
    try {
      const response = await getInsight(machineIdentifier);
      if (!isMountedRef.current) {
        return;
      }
      if (response) {
        setInsightSource("backend");
        setInsightError("");
      } else {
        setInsightSource("fallback");
        setInsightError("AI summary fell back to local insight.");
      }
    } catch {
      if (isMountedRef.current) {
        setInsightSource("fallback");
        setInsightError("AI summary fell back to local insight.");
      }
    } finally {
      if (isMountedRef.current) {
        setInsightLoading(false);
      }
    }
  }, [scopeMachineIdentifier]);

  const refreshAll = useCallback(async (isMountedRef, machineIdentifier = scopeMachineIdentifier) => {
    await Promise.all([
      refreshMachines(isMountedRef),
      refreshAvailableMachines(isMountedRef),
      refreshTelemetry(isMountedRef, machineIdentifier),
      refreshAlerts(isMountedRef, machineIdentifier),
      refreshAnomalies(isMountedRef, machineIdentifier),
      refreshInvestigations(isMountedRef, machineIdentifier),
      refreshInsight(isMountedRef, machineIdentifier),
    ]);
  }, [
    refreshAlerts,
    refreshAnomalies,
    refreshAvailableMachines,
    refreshInsight,
    refreshInvestigations,
    refreshMachines,
    refreshTelemetry,
    scopeMachineIdentifier,
  ]);

  useEffect(() => {
    const isMountedRef = { current: true };
    const bootstrapTimerId = window.setTimeout(() => {
      refreshAll(isMountedRef, scopeMachineIdentifier);
    }, 0);

    const telemetrySocket = createTelemetrySocket({
      onConnect: () => {
        if (isMountedRef.current) {
          setSocketStatus("connected");
          setTelemetryError("");
        }
      },
      onDisconnect: () => {
        if (isMountedRef.current) {
          setSocketStatus("reconnecting");
        }
      },
      onError: () => {
        if (isMountedRef.current) {
          setSocketStatus("reconnecting");
        }
      },
      onTelemetry: (snapshot) => {
        if (!isMountedRef.current) {
          return;
        }

        if (scopeMachineIdentifier && snapshot.machineIdentifier !== scopeMachineIdentifier) {
          refreshMachines(isMountedRef);
          refreshAvailableMachines(isMountedRef);
          return;
        }

        setTelemetryHistory((existingSnapshots) => {
          const nextHistory = mergeSnapshotIntoHistory(existingSnapshots, snapshot);
          setLatestTelemetry(nextHistory[0] || null);
          return nextHistory;
        });

        refreshMachines(isMountedRef);
        refreshAvailableMachines(isMountedRef);
        refreshAlerts(isMountedRef, scopeMachineIdentifier);
        refreshAnomalies(isMountedRef, scopeMachineIdentifier);
        refreshInvestigations(isMountedRef, scopeMachineIdentifier);
        refreshInsight(isMountedRef, scopeMachineIdentifier);
      },
    });

    telemetrySocket.connect();

    return () => {
      isMountedRef.current = false;
      window.clearTimeout(bootstrapTimerId);
      telemetrySocket.disconnect();
    };
  }, [
    authEnabled,
    refreshAlerts,
    refreshAnomalies,
    refreshAvailableMachines,
    refreshAll,
    refreshInsight,
    refreshInvestigations,
    refreshMachines,
    scopeMachineIdentifier,
  ]);

  useEffect(() => {
    const isMountedRef = { current: true };
    const intervalId = window.setInterval(() => {
      refreshAll(isMountedRef, scopeMachineIdentifier);
    }, 5000);

    return () => {
      isMountedRef.current = false;
      window.clearInterval(intervalId);
    };
  }, [refreshAll, scopeMachineIdentifier]);

  const selectedMachine = useMemo(
    () => machines.find((machine) => machine.machineIdentifier === scopeMachineIdentifier) || null,
    [machines, scopeMachineIdentifier]
  );

  const claimSelectedMachine = useCallback(async (machineIdentifier) => {
    setClaimError("");
    try {
      await claimMachine(machineIdentifier);
      const isMountedRef = { current: true };
      await refreshAll(isMountedRef, scopeMachineIdentifier || machineIdentifier);
      return { success: true };
    } catch (error) {
      const message = error?.response?.data?.message || "Unable to claim this machine right now.";
      setClaimError(message);
      return { success: false, message };
    }
  }, [refreshAll, scopeMachineIdentifier]);

  const unclaimSelectedMachine = useCallback(async (machineIdentifier) => {
    setClaimError("");
    try {
      await unclaimMachine(machineIdentifier);
      const isMountedRef = { current: true };
      await refreshAll(isMountedRef, scopeMachineIdentifier === machineIdentifier ? "" : scopeMachineIdentifier);
      return { success: true };
    } catch (error) {
      const message = error?.response?.data?.message || "Unable to unclaim this machine right now.";
      setClaimError(message);
      return { success: false, message };
    }
  }, [refreshAll, scopeMachineIdentifier]);

  return {
    alerts,
    alertsError,
    alertsLoading,
    anomalies,
    anomaliesError,
    anomaliesLoading,
    authEnabled,
    availableMachines,
    claimError,
    claimSelectedMachine,
    insightError,
    insightLoading,
    insightSource,
    investigationsError,
    investigationsLoading,
    latestTelemetry,
    machines,
    machinesError,
    machinesLoading,
    recentInvestigations,
    refreshAll,
    selectedMachine,
    socketStatus,
    telemetryError,
    telemetryHistory,
    telemetryLoading,
    unclaimSelectedMachine,
  };
}
