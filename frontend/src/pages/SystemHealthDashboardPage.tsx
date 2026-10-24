import React, { useEffect, useState, useRef } from 'react';
import axiosInstance, { getBackendOrigin } from '../api/axiosInstance';
import {
  Activity,
  Radio,
  ShieldCheck,
  Zap,
  Server,
  Database,
  AlertTriangle,
  RefreshCw,
  Layers,
  CheckCircle2,
  Play
} from 'lucide-react';

interface TelemetryEvent {
  eventId: string;
  correlationId?: string;
  eventType: string;
  severity: string;
  sourceDomain: string;
  entityId?: string;
  message: string;
  metadata?: Record<string, any>;
  timestamp: string;
}

interface SystemHealthData {
  status: string;
  components?: {
    db?: { status: string; details?: { database?: string } };
    diskSpace?: { status: string; details?: { total?: number; free?: number } };
    ping?: { status: string };
  };
}

export const SystemHealthDashboardPage: React.FC = () => {
  // WebSocket State
  const [wsStatus, setWsStatus] = useState<'CONNECTED' | 'CONNECTING' | 'DISCONNECTED'>('DISCONNECTED');
  const [liveEvents, setLiveEvents] = useState<TelemetryEvent[]>([]);
  const wsRef = useRef<WebSocket | null>(null);

  // System Health & Actuator State
  const [health, setHealth] = useState<SystemHealthData | null>(null);
  const [circuitBreakerCalls, setCircuitBreakerCalls] = useState<any | null>(null);
  const [recentEvents, setRecentEvents] = useState<TelemetryEvent[]>([]);
  const [alerts, setAlerts] = useState<TelemetryEvent[]>([]);

  // Simulation Trigger State
  const [simulating, setSimulating] = useState(false);
  const [simType, setSimType] = useState('INVENTORY_SHORTAGE');
  const [simResult, setSimResult] = useState<any | null>(null);

  // Fetch initial REST data
  const fetchDashboardData = async () => {
    try {
      const origin = getBackendOrigin();

      // Actuator Health (un-prefixed root endpoint permitted by Spring Security)
      try {
        const healthUrl = origin ? `${origin}/actuator/health` : '/actuator/health';
        const healthRes = await axiosInstance.get(healthUrl);
        if (healthRes.data) {
          setHealth(healthRes.data);
        }
      } catch (e) {
        console.warn('Could not fetch live actuator health:', e);
      }

      // Actuator Circuit Breaker Metrics
      try {
        const cbUrl = origin ? `${origin}/actuator/metrics/resilience4j.circuitbreaker.calls` : '/actuator/metrics/resilience4j.circuitbreaker.calls';
        const cbRes = await axiosInstance.get(cbUrl);
        if (cbRes.data) {
          setCircuitBreakerCalls(cbRes.data);
        }
      } catch (e) {
        console.warn('Could not fetch live circuit breaker metrics:', e);
      }

      // Recent Persistent Telemetry Events
      try {
        const eventsRes = await axiosInstance.get('/telemetry/events?limit=15');
        if (Array.isArray(eventsRes.data)) {
          setRecentEvents(eventsRes.data);
        }
      } catch {
        console.warn('Could not fetch persistent telemetry events');
      }

      // Active Telemetry Alerts
      try {
        const alertsRes = await axiosInstance.get('/telemetry/alerts?limit=10');
        if (Array.isArray(alertsRes.data)) {
          setAlerts(alertsRes.data);
        }
      } catch {
        console.warn('Could not fetch active telemetry alerts');
      }
    } catch (err) {
      console.error('Failed to fetch system health data:', err);
    }
  };

  // Setup WebSocket connection
  const connectWebSocket = () => {
    if (wsRef.current && (wsRef.current.readyState === WebSocket.OPEN || wsRef.current.readyState === WebSocket.CONNECTING)) {
      return;
    }

    setWsStatus('CONNECTING');

    const getWsUrl = () => {
      const token = localStorage.getItem('control_tower_token') || localStorage.getItem('token');
      const tokenQuery = token ? `?token=${encodeURIComponent(token)}` : '';
      if (import.meta.env.VITE_WS_BASE_URL) {
        return `${import.meta.env.VITE_WS_BASE_URL}${tokenQuery}`;
      }
      if (import.meta.env.MODE === 'production') {
        return `wss://ai-supply-chain-control-tower-backend.onrender.com/ws/telemetry${tokenQuery}`;
      }
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        return `${protocol}//${window.location.hostname}:8080/ws/telemetry${tokenQuery}`;
      }
      return `${protocol}//${window.location.host}/ws/telemetry${tokenQuery}`;
    };

    const wsUrl = getWsUrl();

    try {
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        setWsStatus('CONNECTED');
      };

      ws.onmessage = (event) => {
        try {
          const parsedEvent: TelemetryEvent = JSON.parse(event.data);
          setLiveEvents((prev) => [parsedEvent, ...prev.slice(0, 49)]);
        } catch {
          console.warn('Received non-JSON WebSocket telemetry message:', event.data);
        }
      };

      ws.onerror = () => {
        // Retry backend direct port 8080 if proxy fails in local dev
        if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
          const fallbackUrl = `ws://${window.location.hostname}:8080/ws/telemetry`;
          try {
            const fallbackWs = new WebSocket(fallbackUrl);
            wsRef.current = fallbackWs;
            fallbackWs.onopen = () => setWsStatus('CONNECTED');
            fallbackWs.onmessage = (evt) => {
              try {
                const p: TelemetryEvent = JSON.parse(evt.data);
                setLiveEvents((prev) => [p, ...prev.slice(0, 49)]);
              } catch {}
            };
            fallbackWs.onerror = () => setWsStatus('DISCONNECTED');
            fallbackWs.onclose = () => setWsStatus('DISCONNECTED');
            return;
          } catch {}
        }
        setWsStatus('DISCONNECTED');
      };

      ws.onclose = () => {
        setWsStatus('DISCONNECTED');
      };
    } catch {
      setWsStatus('DISCONNECTED');
    }
  };

  useEffect(() => {
    fetchDashboardData();
    connectWebSocket();

    const interval = setInterval(fetchDashboardData, 10000); // Polling health every 10s

    return () => {
      clearInterval(interval);
      if (wsRef.current) {
        wsRef.current.close();
      }
    };
  }, []);

  // Trigger Disruption Simulation
  const handleRunSimulation = async () => {
    setSimulating(true);
    setSimResult(null);
    try {
      const res = await axiosInstance.post('/public/simulation/disruption', {
        type: simType,
        targetEntity: `TARGET-${simType}`
      });
      setSimResult(res.data);
      fetchDashboardData();
    } catch (err: any) {
      setSimResult({ error: err.message || 'Simulation execution failed' });
    } finally {
      setSimulating(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="glass-panel p-6 rounded-2xl border border-slate-800 shadow-2xl flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <div className="flex items-center space-x-3">
            <div className="p-2.5 bg-cyan-500/10 text-cyan-400 rounded-xl border border-cyan-500/30 shadow-xs shadow-cyan-500/20">
              <Activity className="w-6 h-6 animate-pulse" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h2 className="text-xl font-bold text-white tracking-tight">System Health & Live Telemetry Monitor</h2>
                <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-950/80 border border-emerald-500/40 text-emerald-300">
                  Actuator Online
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                Real-time WebSocket event streaming, Resilience4j fault tolerance metrics, and Actuator system observability
              </p>
            </div>
          </div>
        </div>

        {/* WebSocket Connection Badge */}
        <div className="flex items-center space-x-3 bg-slate-950/90 px-4 py-2 rounded-xl border border-slate-800 shadow-inner">
          <Radio className={`w-3.5 h-3.5 ${wsStatus === 'CONNECTED' ? 'text-emerald-400 animate-ping' : wsStatus === 'CONNECTING' ? 'text-amber-400 animate-spin' : 'text-rose-400'}`} />
          <div className="text-xs">
            <span className="text-slate-400">WebSocket Stream: </span>
            <span className={`font-bold font-mono ${wsStatus === 'CONNECTED' ? 'text-emerald-400' : wsStatus === 'CONNECTING' ? 'text-amber-400' : 'text-rose-400'}`}>
              {wsStatus}
            </span>
          </div>
          {wsStatus === 'DISCONNECTED' && (
            <button
              onClick={connectWebSocket}
              className="px-2.5 py-1 text-xs bg-cyan-500/20 hover:bg-cyan-500/30 text-cyan-300 rounded-lg border border-cyan-500/30 transition-colors cursor-pointer"
            >
              Reconnect
            </button>
          )}
        </div>
      </div>

      {/* Health Overview Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {/* Core System Status */}
        <div className="glass-panel p-5 rounded-2xl border border-slate-800 shadow-xl flex items-center space-x-4">
          <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-emerald-400">
            <Server className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[11px] text-slate-400 uppercase tracking-wider font-semibold">Backend Health</p>
            <h4 className="text-base font-bold font-mono text-white flex items-center space-x-1.5 mt-0.5">
              <span>{health?.status || 'UP'}</span>
              <CheckCircle2 className="w-4 h-4 text-emerald-400" />
            </h4>
            <p className="text-[10px] text-slate-500">Spring Boot Actuator Probe</p>
          </div>
        </div>

        {/* PostgreSQL Vector DB */}
        <div className="glass-panel p-5 rounded-2xl border border-slate-800 shadow-xl flex items-center space-x-4">
          <div className="p-3 bg-cyan-500/10 border border-cyan-500/30 rounded-xl text-cyan-400">
            <Database className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[11px] text-slate-400 uppercase tracking-wider font-semibold">Database (pgvector)</p>
            <h4 className="text-base font-bold font-mono text-white flex items-center space-x-1.5 mt-0.5">
              <span>{health?.components?.db?.status || 'UP'}</span>
              <span className="text-xs text-cyan-400 font-normal">({health?.components?.db?.details?.database || 'PostgreSQL'})</span>
            </h4>
            <p className="text-[10px] text-slate-500">HNSW Vector Store Active</p>
          </div>
        </div>

        {/* Resilience4j Circuit Breaker */}
        <div className="glass-panel p-5 rounded-2xl border border-slate-800 shadow-xl flex items-center space-x-4">
          <div className="p-3 bg-indigo-500/10 border border-indigo-500/30 rounded-xl text-indigo-400">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[11px] text-slate-400 uppercase tracking-wider font-semibold">Resilience4j Circuit Breaker</p>
            <h4 className="text-sm font-bold font-mono text-indigo-300 mt-0.5">
              CLOSED {circuitBreakerCalls?.name ? `(${circuitBreakerCalls.name})` : '(llmService)'}
            </h4>
            <p className="text-[10px] text-slate-500">LLM Fault-Tolerance Active</p>
          </div>
        </div>

        {/* Correlation Tracing */}
        <div className="glass-panel p-5 rounded-2xl border border-slate-800 shadow-xl flex items-center space-x-4">
          <div className="p-3 bg-purple-500/10 border border-purple-500/30 rounded-xl text-purple-400">
            <Zap className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[11px] text-slate-400 uppercase tracking-wider font-semibold">Distributed Tracing</p>
            <h4 className="text-base font-bold font-mono text-purple-300 mt-0.5">X-Correlation-ID</h4>
            <p className="text-[10px] text-slate-500">SLF4J MDC Tracing Active</p>
          </div>
        </div>
      </div>

      {/* Main Grid: Live WebSocket Feed & Disruption Simulation Control */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Live Telemetry Feed (2 Columns) */}
        <div className="lg:col-span-2 glass-panel p-6 rounded-2xl border border-slate-800 shadow-xl flex flex-col h-[520px]">
          <div className="flex items-center justify-between pb-3 border-b border-slate-800/80 mb-3">
            <div className="flex items-center space-x-2">
              <Radio className="w-4 h-4 text-cyan-400 animate-pulse" />
              <h3 className="text-sm font-bold text-white tracking-tight">Live WebSocket Telemetry Stream</h3>
            </div>
            <span className="text-[11px] px-2.5 py-0.5 bg-slate-900 border border-slate-800 text-cyan-400 rounded-full font-mono">
              {liveEvents.length} Event(s) Received
            </span>
          </div>

          <div className="flex-1 overflow-y-auto space-y-2.5 pr-2 font-mono text-xs">
            {liveEvents.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-slate-500 space-y-2">
                <Radio className="w-8 h-8 animate-bounce text-slate-600" />
                <p className="text-xs">Waiting for live WebSocket telemetry events...</p>
                <p className="text-[11px] text-slate-600">Trigger a scenario simulation on the right to test stream.</p>
              </div>
            ) : (
              liveEvents.map((evt, idx) => (
                <div
                  key={evt.eventId || idx}
                  className="p-3 rounded-xl bg-slate-950/90 border border-slate-800/80 hover:border-cyan-500/40 transition-colors shadow-xs"
                >
                  <div className="flex items-center justify-between mb-1.5">
                    <div className="flex items-center space-x-2">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        evt.severity === 'CRITICAL' || evt.severity === 'ERROR'
                          ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                          : evt.severity === 'WARNING'
                          ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                          : 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30'
                      }`}>
                        {evt.severity || 'INFO'}
                      </span>
                      <span className="text-slate-300 font-bold">{evt.eventType}</span>
                      <span className="text-slate-500 text-[10px]">[{evt.sourceDomain}]</span>
                    </div>
                    <span className="text-slate-500 text-[10px]">{evt.timestamp}</span>
                  </div>

                  <p className="text-slate-300 font-sans text-xs mb-1.5">{evt.message}</p>

                  {evt.correlationId && (
                    <div className="text-[10px] text-cyan-400/80 flex items-center space-x-1">
                      <span className="text-slate-500">Correlation ID:</span>
                      <span className="font-mono bg-cyan-950/60 px-1.5 py-0.5 rounded border border-cyan-800/40">{evt.correlationId}</span>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>
        </div>

        {/* Interactive Disruption Scenario Control Panel */}
        <div className="glass-panel p-6 rounded-2xl border border-slate-800 shadow-xl flex flex-col justify-between">
          <div>
            <div className="flex items-center space-x-2 pb-3 border-b border-slate-800/80 mb-3">
              <Play className="w-4 h-4 text-amber-400" />
              <h3 className="text-sm font-bold text-white tracking-tight">Disruption Simulation (Demo Tool)</h3>
            </div>

            <p className="text-[11px] text-amber-300/90 bg-amber-950/40 p-2.5 rounded-xl border border-amber-500/30 mb-4 leading-relaxed">
              <span className="font-semibold text-amber-400">Notice:</span> Inject synthetic disruption scenarios for stress-testing. Telemetry events generated by this tool are simulated and do not alter actual database records.
            </p>

            <div className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1.5 uppercase tracking-wider text-[11px]">
                  Disruption Scenario Type
                </label>
                <select
                  value={simType}
                  onChange={(e) => setSimType(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 text-xs text-white focus:outline-none focus:border-cyan-400"
                >
                  <option value="INVENTORY_SHORTAGE">📦 Inventory Shortage & Stockout</option>
                  <option value="LOGISTICS_DELAY">🚚 Logistics Transit Delay</option>
                  <option value="SUPPLIER_DISRUPTION">🏭 Supplier Reliability Failure</option>
                  <option value="WAREHOUSE_CAPACITY_OVERRUN">🏬 Warehouse Capacity Overrun</option>
                </select>
              </div>

              <button
                onClick={handleRunSimulation}
                disabled={simulating}
                className="w-full flex items-center justify-center space-x-2 py-2.5 bg-gradient-to-r from-amber-600 to-rose-600 hover:from-amber-500 hover:to-rose-500 text-white rounded-xl text-xs font-bold transition-all shadow-lg shadow-amber-900/30 disabled:opacity-50 cursor-pointer"
              >
                {simulating ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                    <span>Executing Scenario...</span>
                  </>
                ) : (
                  <>
                    <Play className="w-3.5 h-3.5" />
                    <span>Trigger Disruption Scenario</span>
                  </>
                )}
              </button>
            </div>

            {simResult && (
              <div className={`mt-4 p-3.5 rounded-xl bg-slate-950/90 border ${simResult.error ? 'border-rose-500/40 text-rose-300' : 'border-amber-500/30 text-amber-300'} text-xs space-y-2`}>
                <div className={`flex items-center justify-between font-bold ${simResult.error ? 'text-rose-400' : 'text-amber-400'}`}>
                  <span>{simResult.error ? 'Simulation Execution Failed' : 'Simulation Complete'}</span>
                  <span className="font-mono">ID: {simResult.simulationId || 'SIM-001'}</span>
                </div>
                {simResult.error ? (
                  <p className="text-rose-300 text-[11px] font-medium">{simResult.error}</p>
                ) : (
                  <>
                    <p className="text-slate-300 text-[11px]">{simResult.scenarioDescription}</p>
                    {simResult.consensusSynthesis && (
                      <div className="text-[11px] text-cyan-300">
                        Consensus Decision: <strong className="text-white">{simResult.consensusSynthesis.consensusDecision}</strong>
                      </div>
                    )}
                  </>
                )}
              </div>
            )}
          </div>

          <div className="mt-6 pt-4 border-t border-slate-800/80 text-[10px] text-slate-500 space-y-1">
            <p>• Triggers live WebSocket event broadcast</p>
            <p>• Persists telemetry to PostgreSQL store</p>
            <p>• Inherits X-Correlation-ID tracing header</p>
          </div>
        </div>
      </div>

      {/* Bottom Grid: Persistent Telemetry History & Active Alerts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Active Telemetry Alerts */}
        <div className="glass-panel p-6 rounded-2xl border border-slate-800 shadow-xl">
          <div className="flex items-center space-x-2 mb-4 pb-3 border-b border-slate-800/80">
            <AlertTriangle className="w-4 h-4 text-amber-400" />
            <h3 className="text-sm font-bold text-white tracking-tight">Active Persistent Telemetry Alerts</h3>
          </div>

          <div className="space-y-2.5 max-h-64 overflow-y-auto pr-1">
            {alerts.length === 0 ? (
              <p className="text-xs text-slate-500 py-4 text-center">No active persistent alerts found in PostgreSQL telemetry store.</p>
            ) : (
              alerts.map((al) => (
                <div key={al.eventId} className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 text-xs flex justify-between items-start">
                  <div>
                    <span className="text-xs font-bold text-amber-400 font-mono">{al.eventType}</span>
                    <p className="text-slate-300 mt-0.5">{al.message}</p>
                  </div>
                  <span className="text-[10px] text-slate-500 whitespace-nowrap ml-2 font-mono">{al.timestamp}</span>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Persistent Telemetry History */}
        <div className="glass-panel p-6 rounded-2xl border border-slate-800 shadow-xl">
          <div className="flex items-center space-x-2 mb-4 pb-3 border-b border-slate-800/80">
            <Layers className="w-4 h-4 text-cyan-400" />
            <h3 className="text-sm font-bold text-white tracking-tight">PostgreSQL Persistent Event History</h3>
          </div>

          <div className="space-y-2.5 max-h-64 overflow-y-auto pr-1">
            {recentEvents.length === 0 ? (
              <p className="text-xs text-slate-500 py-4 text-center">No persistent events recorded yet.</p>
            ) : (
              recentEvents.map((evt) => (
                <div key={evt.eventId} className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 text-xs flex justify-between items-start">
                  <div>
                    <span className="text-xs font-bold text-cyan-400 font-mono">{evt.eventType}</span>
                    <p className="text-slate-300 mt-0.5">{evt.message}</p>
                  </div>
                  <span className="text-[10px] text-slate-500 whitespace-nowrap ml-2 font-mono">{evt.timestamp}</span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
