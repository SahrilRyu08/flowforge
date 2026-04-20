import { useCallback, useEffect, useMemo, useState } from "react";
import ReactFlow, { Background, Controls, MiniMap, Node, Edge } from "reactflow";
import "reactflow/dist/style.css";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

type Overview = {
  activeRuns: number;
  runsLast24h: number;
  successLast24h: number;
  failedLast24h: number;
  successRateLast24h: number;
  avgDurationMsLast24h: number;
};

const apiBase = import.meta.env.VITE_API_BASE ?? "";

export default function App() {
  const [token, setToken] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [tenantSlug, setTenantSlug] = useState("system");
  const [overview, setOverview] = useState<Overview | null>(null);
  const [events, setEvents] = useState<string[]>([]);
  const [dagJson, setDagJson] = useState(`{
  "steps": [
    { "id": "a", "name": "Delay A", "type": "DELAY", "config": { "durationMs": 50 } },
    { "id": "b", "name": "Delay B", "type": "DELAY", "config": { "durationMs": 50 } }
  ],
  "edges": [ { "from": "a", "to": "b" } ]
}`);

  const nodes: Node[] = useMemo(
    () => [
      { id: "a", position: { x: 0, y: 0 }, data: { label: "Step A" } },
      { id: "b", position: { x: 200, y: 0 }, data: { label: "Step B" } },
    ],
    []
  );
  const edges: Edge[] = useMemo(
    () => [{ id: "e-a-b", source: "a", target: "b" }],
    []
  );

  const login = useCallback(async () => {
    const res = await fetch(`${apiBase}/api/v1/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, tenantSlug }),
    });
    const data = await res.json();
    setToken(data.accessToken);
    setTenantId(data.user.tenant);
  }, [email, password, tenantSlug]);

  useEffect(() => {
    if (!token || !tenantId) return;
    fetch(`${apiBase}/api/v1/dashboard/overview`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((r) => r.json())
      .then(setOverview)
      .catch(console.error);
  }, [token, tenantId]);

  useEffect(() => {
    if (!token || !tenantId) return;
    const client = new Client({
      webSocketFactory: () =>
        new SockJS(`${apiBase}/ws?token=${encodeURIComponent(token)}`) as WebSocket,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/tenant/${tenantId}/runs`, (msg) => {
          setEvents((e) => [`${msg.body}`, ...e].slice(0, 50));
        });
      },
    });
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [token, tenantId]);

  return (
    <div style={{ padding: "1rem", maxWidth: 1100, margin: "0 auto" }}>
      <h1>FlowForge</h1>
      <p>Live dashboard (MVP): login, health metrics, DAG preview, WebSocket run events.</p>

      <section style={{ marginBottom: "1.5rem" }}>
        <h2>Login</h2>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <input
            placeholder="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            style={{ padding: 8, minWidth: 200 }}
          />
          <input
            type="password"
            placeholder="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            style={{ padding: 8 }}
          />
          <input
            placeholder="tenant slug"
            value={tenantSlug}
            onChange={(e) => setTenantSlug(e.target.value)}
            style={{ padding: 8 }}
          />
          <button type="button" onClick={login}>
            Sign in
          </button>
        </div>
      </section>

      {overview && (
        <section style={{ marginBottom: "1.5rem" }}>
          <h2>24h health</h2>
          <ul>
            <li>Active runs: {overview.activeRuns}</li>
            <li>Runs (24h): {overview.runsLast24h}</li>
            <li>Success / failed: {overview.successLast24h} / {overview.failedLast24h}</li>
            <li>Success rate: {(overview.successRateLast24h * 100).toFixed(1)}%</li>
            <li>Avg duration (success): {overview.avgDurationMsLast24h.toFixed(0)} ms</li>
          </ul>
        </section>
      )}

      <section style={{ marginBottom: "1.5rem" }}>
        <h2>DAG preview (sample)</h2>
        <div style={{ height: 280, border: "1px solid #30363d", borderRadius: 8 }}>
          <ReactFlow nodes={nodes} edges={edges} fitView>
            <MiniMap />
            <Controls />
            <Background />
          </ReactFlow>
        </div>
        <p style={{ fontSize: 12, opacity: 0.8 }}>
          Workflow definitions from the API can be mapped to React Flow nodes/edges (same graph model as the engine).
        </p>
      </section>

      <section>
        <h2>WebSocket events</h2>
        <pre
          style={{
            background: "#161b22",
            padding: 12,
            maxHeight: 200,
            overflow: "auto",
            fontSize: 12,
          }}
        >
          {events.length === 0 ? "Connect to see RUN_STARTED / STEP_* / RUN_FINISHED…" : events.join("\n")}
        </pre>
      </section>

      <section style={{ marginTop: "1.5rem" }}>
        <h2>Sample DAG JSON</h2>
        <textarea
          value={dagJson}
          onChange={(e) => setDagJson(e.target.value)}
          rows={10}
          style={{ width: "100%", fontFamily: "monospace", fontSize: 12 }}
        />
      </section>
    </div>
  );
}
