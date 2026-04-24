import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

const SOCKET_URL = "http://localhost:8089/ws";
const TELEMETRY_TOPIC = "/topic/telemetry";

export function createTelemetrySocket({ onTelemetry, onConnect, onDisconnect, onError }) {
  const client = new Client({
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    webSocketFactory: () => new SockJS(SOCKET_URL),
  });

  client.onConnect = () => {
    onConnect?.();

    client.subscribe(TELEMETRY_TOPIC, (message) => {
      try {
        const payload = JSON.parse(message.body);
        onTelemetry?.(payload);
      } catch (error) {
        onError?.(error);
      }
    });
  };

  client.onStompError = (frame) => {
    onError?.(new Error(frame.headers.message || "STOMP connection error"));
  };

  client.onWebSocketClose = () => {
    onDisconnect?.();
  };

  client.onWebSocketError = (event) => {
    onError?.(event);
  };

  return {
    connect() {
      client.activate();
    },
    disconnect() {
      client.deactivate();
    },
  };
}
