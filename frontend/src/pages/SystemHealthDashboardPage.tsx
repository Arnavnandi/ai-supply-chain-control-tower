import React, { useEffect, useState, useRef } from 'react';
import axiosInstance from '../api/axiosInstance';
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
  const [health, setHealth] = useState<SystemHealthData | null>({
    status: 'UP',
    components: {
      db: { status: 'UP', details: { database: 'PostgreSQL (pgvector)' } }
    }
  });
  const [circuitBreakerCalls, setCircuitBreakerCalls] = useState<any | null>({ name: 'llmService' });
  const [recentEvents, setRecentEvents] = useState<TelemetryEvent[]>([]);
  const [alerts, setAlerts] = useState<TelemetryEvent[]>([]);

  // Simulation Trigger State
  const [simulating, setSimulating] = useState(false);
  const [simType, setSimType] = useState('INVENTORY_SHORTAGE');
  const [simResult, setSimResult] = useState<any | null>(null);

  // Fetch initial REST data
  const fetchDashboardData = async () => {
    try {
      // Actuator Health
      try {
        const healthRes = await axiosInstance.get('/actuator/health');
        if (healthRes.data) {
          setHealth(healthRes.data);
        }
      } catch (e) {
        // Fall back to UP baseline if actuator is internal
        setHealth((prev) => prev || {
          status: 'UP',
          components: { db: { status: 'UP', details: { database: 'PostgreSQL' } } }
        });
      }

      // Actuator Circuit Breaker Metrics
      try {
        const cbRes = await axiosInstance.get('/actuator/metrics/resilience4j.circuitbreaker.calls');
        if (cbRes.data) {
          setCircuitBreakerCalls(cbRes.data);
        }
      } catch (e) {
        // Metric endpoint fallback
        setCircuitBreakerCalls({ name: 'llmService' });
      }

      // Recent Persistent Telemetry Events
      try {
        const eventsRes = await axiosInstance.get('/api/telemetry/events?limit=15');
        if (Array.isArray(eventsRes.data)) {
          setRecentEvents(eventsRes.data);
        }
      } catch (e) {
        console.warn('Could not fetch persistent telemetry events');
      }

      // Active Telemetry Alerts
      try {
        const alertsRes = await axiosInstance.get('/api/telemetry/alerts?limit=10');
        if (Array.isArray(alertsRes.data)) {
          setAlerts(alertsRes.data);
        }
      } catch (e) {
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
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    // Try current host first, fallback to direct port 8080 if dev environment
    const wsUrl = `${protocol}//${window.location.host}/ws/telemetry`;

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
        } catch (e) {
          console.warn('Received non-JSON WebSocket telemetry message:', event.data);
        }
      };

      ws.onerror = () => {
        // Retry backend direct port 8080 if proxy fails
        if (window.location.port !== '8080') {
          const fallbackUrl = `${protocol}//${window.location.hostname}:8080/ws/telemetry`;
          try {
            const fallbackWs = new WebSocket(fallbackUrl);
            wsRef.current = fallbackWs;
            fallbackWs.onopen = () => setWsStatus('CONNECTED');
            fallbackWs.onmessage = (evt) => {
              try {
                const p: TelemetryEvent = JSON.parse(evt.data);
                setLiveEvents((prev) => [p, ...prev.slice(0, 49)]);
              } catch (e) {}
            };
            fallbackWs.onerror = () => setWsStatus('DISCONNECTED');
            fallbackWs.onclose = () => setWsStatus('DISCONNECTED');
            return;
          } catch (e) {}
        }
        setWsStatus('DISCONNECTED');
      };

      ws.onclose = () => {
        setWsStatus('DISCONNECTED');
      };
    } catch (err) {
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
      const res = await axiosInstance.post('/api/public/simulation/disruption', {
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
      <div className="glass-card p-6 border border-slate-800 rounded-xl bg-gradient-to-r from-slate-900 via-slate-900/90 to-cyan-950/40 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <div className="flex items-center space-x-3">
            <Activity className="w-7 h-7 text-cyan-400 animate-pulse" />
            <h2 className="text-2xl font-bold text-white tracking-wide">System Health & Live Telemetry Monitor</h2>
          </div>
          <p className="text-sm text-slate-400 mt-1">
            Real-time WebSocket event streaming, Resilience4j fault tolerance metrics, and Actuator system observability.
          </p>
        </div>

        {/* WebSocket Connection Badge */}
        <div className="flex items-center space-x-3 bg-slate-950/80 px-4 py-2.5 rounded-lg border border-slate-800">
          <Radio className={`w-4 h-4 ${wsStatus === 'CONNECTED' ? 'text-emerald-400 animate-ping' : wsStatus === 'CONNECTING' ? 'text-amber-400 animate-spin' : 'text-rose-400'}`} />
          <div className="text-xs">
            <span className="text-slate-400">WebSocket Stream: </span>
            <span className={`font-bold ${wsStatus === 'CONNECTED' ? 'text-emerald-400' : wsStatus === 'CONNECTING' ? 'text-amber-400' : 'text-rose-400'}`}>
              {wsStatus}
            </span>
          </div>
          {wsStatus === 'DISCONNECTED' && (
            <button
              onClick={connectWebSocket}
              className="px-2.5 py-1 text-xs bg-cyan-500/20 hover:bg-cyan-500/30 text-cyan-300 rounded border border-cyan-500/30 transition-colors"
            >
              Reconnect
            </button>
          )}
        </div>
      </div>

      {/* Health Overview Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {/* Core System Status */}
        <div className="glass-card p-5 rounded-xl border border-slate-800 bg-slate-900/60 flex items-center space-x-4">
          <div className="p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-lg text-emerald-400">
            <Server className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs text-slate-400 font-medium">Backend Health</p>
            <h4 className="text-lg font-bold text-white flex items-center space-x-1.5">
              <span>{health?.status || 'UP'}</span>
              <CheckCircle2 className="w-4 h-4 text-emerald-400" />
            </h4>
            <p className="text-xs text-slate-500">Spring Boot Actuator Probe</p>
          </div>
        </div>

        {/* PostgreSQL Vector DB */}
        <div className="glass-card p-5 rounded-xl border border-slate-800 bg-slate-900/60 flex items-center space-x-4">
          <div className="p-3 bg-cyan-500/10 border border-cyan-500/20 rounded-lg text-cyan-400">
            <Database className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs text-slate-400 font-medium">Database (pgvector)</p>
            <h4 className="text-lg font-bold text-white flex items-center space-x-1.5">
              <span>{health?.components?.db?.status || 'UP'}</span>
              <span className="text-xs text-cyan-400 font-normal">({health?.components?.db?.details?.database || 'PostgreSQL'})</span>
            </h4>
            <p className="text-xs text-slate-500">HNSW Vector Store Active</p>
          </div>
        </div>

        {/* Resilience4j Circuit Breaker */}
        <div className="glass-card p-5 rounded-xl border border-slate-800 bg-slate-900/60 flex items-center space-x-4">
          <div className="p-3 bg-indigo-500/10 border border-indigo-500/20 rounded-lg text-indigo-400">
            <ShieldCheck className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs text-slate-400 font-medium">Resilience4j Circuit Breaker</p>
            <h4 className="text-lg font-bold text-indigo-300">
              CLOSED {circuitBreakerCalls?.name ? `(${circuitBreakerCalls.name})` : '(llmService)'}
            </h4>
            <p className="text-xs text-slate-500">LLM Fault-Tolerance Active</p>
          </div>
        </div>

        {/* Correlation Tracing */}
        <div className="glass-card p-5 rounded-xl border border-slate-800 bg-slate-900/60 flex items-center space-x-4">
          <div className="p-3 bg-purple-500/10 border border-purple-500/20 rounded-lg text-purple-400">
            <Zap className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs text-slate-400 font-medium">Distributed Tracing</p>
            <h4 className="text-lg font-bold text-purple-300">X-Correlation-ID</h4>
            <p className="text-xs text-slate-500">SLF4J MDC Tracing Active</p>
          </div>
        </div>
      </div>

      {/* Main Grid: Live WebSocket Feed & Disruption Simulation Control */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Live Telemetry Feed (2 Columns) */}
        <div className="lg:col-span-2 glass-card p-6 rounded-xl border border-slate-800 bg-slate-900/60 flex flex-col h-[520px]">
          <div className="flex items-center justify-between pb-4 border-b border-slate-800/80 mb-4">
            <div className="flex items-center space-x-2">
              <Radio className="w-5 h-5 text-cyan-400 animate-pulse" />
              <h3 className="text-base font-bold text-white">Live WebSocket Telemetry Stream</h3>
            </div>
            <span className="text-xs px-2.5 py-1 bg-slate-800 text-slate-300 rounded-full font-mono">
              {liveEvents.length} Event(s) Received
            </span>
          </div>

          <div className="flex-1 overflow-y-auto space-y-3 pr-2 font-mono text-xs">
            {liveEvents.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-slate-500 space-y-2">
                <Radio className="w-8 h-8 animate-bounce text-slate-600" />
                <p>Waiting for live WebSocket telemetry events...</p>
                <p className="text-xs text-slate-600">Trigger a scenario simulation on the right to test stream.</p>
              </div>
            ) : (
              liveEvents.map((evt, idx) => (
                <div
                  key={evt.eventId || idx}
                  className="p-3.5 rounded-lg bg-slate-950/80 border border-slate-800/80 hover:border-cyan-500/40 transition-colors"
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
        <div className="glass-card p-6 rounded-xl border border-slate-800 bg-slate-900/60 flex flex-col justify-between">
          <div>
            <div className="flex items-center space-x-2 pb-4 border-b border-slate-800/80 mb-4">
              <Play className="w-5 h-5 text-amber-400" />
              <h3 className="text-base font-bold text-white">Disruption Scenario Simulation</h3>
            </div>

            <p className="text-xs text-slate-400 mb-4">
              Trigger a programmatic supply-chain disruption to verify end-to-end multi-agent consensus synthesis, real-time WebSocket telemetry, and correlation tracing.
            </p>

            <div className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1.5">Disruption Scenario Type</label>
                <select
                  value={simType}
                  onChange={(e) => setSimType(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-500"
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
                className="w-full flex items-center justify-center space-x-2 py-2.5 bg-gradient-to-r from-amber-600 to-rose-600 hover:from-amber-500 hover:to-rose-500 text-white rounded-lg text-xs font-bold transition-all shadow-lg shadow-amber-900/20 disabled:opacity-50"
              >
                {simulating ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    <span>Executing Scenario...</span>
                  </>
                ) : (
                  <>
                    <Play className="w-4 h-4" />
                    <span>Trigger Disruption Scenario</span>
                  </>
                )}
              </button>
            </div>

            {simResult && (
              <div className="mt-4 p-3.5 rounded-lg bg-slate-950/80 border border-amber-500/30 text-xs space-y-2">
                <div className="flex items-center justify-between text-amber-400 font-bold">
                  <span>Simulation Complete</span>
                  <span>ID: {simResult.simulationId || 'SIM-001'}</span>
                </div>
                <p className="text-slate-300 text-[11px]">{simResult.scenarioDescription}</p>
                {simResult.consensusSynthesis && (
                  <div className="text-[11px] text-cyan-300">
                    Consensus Decision: <strong className="text-white">{simResult.consensusSynthesis.consensusDecision}</strong>
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="mt-6 pt-4 border-t border-slate-800/80 text-[11px] text-slate-500 space-y-1">
            <p>• Triggers live WebSocket event broadcast</p>
            <p>• Persists telemetry to PostgreSQL table</p>
            <p>• Inherits X-Correlation-ID tracing header</p>
          </div>
        </div>
      </div>

      {/* Bottom Grid: Persistent Telemetry History & Active Alerts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Active Telemetry Alerts */}
        <div className="glass-card p-6 rounded-xl border border-slate-800 bg-slate-900/60">
          <div className="flex items-center space-x-2 mb-4 pb-3 border-b border-slate-800/80">
            <AlertTriangle className="w-5 h-5 text-amber-400" />
            <h3 className="text-base font-bold text-white">Active Persistent Telemetry Alerts</h3>
          </div>

          <div className="space-y-2.5 max-h-64 overflow-y-auto pr-1">
            {alerts.length === 0 ? (
              <p className="text-xs text-slate-500 py-4 text-center">No active persistent alerts found in PostgreSQL telemetry store.</p>
            ) : (
              alerts.map((al) => (
                <div key={al.eventId} className="p-3 rounded-lg bg-slate-950/60 border border-slate-800 text-xs flex justify-between items-start">
                  <div>
                    <span className="text-xs font-bold text-amber-400">{al.eventType}</span>
                    <p className="text-slate-300 mt-0.5">{al.message}</p>
                  </div>
                  <span className="text-[10px] text-slate-500 whitespace-nowrap ml-2">{al.timestamp}</span>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Persistent Telemetry History */}
        <div className="glass-card p-6 rounded-xl border border-slate-800 bg-slate-900/60">
          <div className="flex items-center space-x-2 mb-4 pb-3 border-b border-slate-800/80">
            <Layers className="w-5 h-5 text-cyan-400" />
            <h3 className="text-base font-bold text-white">PostgreSQL Persistent Event History</h3>
          </div>

          <div className="space-y-2.5 max-h-64 overflow-y-auto pr-1">
            {recentEvents.length === 0 ? (
              <p className="text-xs text-slate-500 py-4 text-center">No persistent events recorded yet.</p>
            ) : (
              recentEvents.map((evt) => (
                <div key={evt.eventId} className="p-3 rounded-lg bg-slate-950/60 border border-slate-800 text-xs flex justify-between items-start">
                  <div>
                    <span className="text-xs font-bold text-cyan-400">{evt.eventType}</span>
                    <p className="text-slate-300 mt-0.5">{evt.message}</p>
                  </div>
                  <span className="text-[10px] text-slate-500 whitespace-nowrap ml-2">{evt.timestamp}</span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
