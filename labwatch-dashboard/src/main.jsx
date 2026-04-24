// 🔥 FIX: SockJS / STOMP needs global in browser (Vite doesn't provide it)
window.global = window;

import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.jsx";

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);