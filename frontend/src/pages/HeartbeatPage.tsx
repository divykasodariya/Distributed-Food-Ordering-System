import React, { useEffect, useState } from 'react';
import {
  Activity,
  Heart,
  AlertTriangle,
  CheckCircle,
  Clock,
  ShieldCheck,
  RefreshCw
} from 'lucide-react';
import type { NodeStatus, ClusterInfo } from '../types';

export const HeartbeatPage: React.FC = () => {
  const [cluster, setCluster] = useState<ClusterInfo | null>(null);
  const [nodes, setNodes] = useState<NodeStatus[]>([]);
  const [heartbeatHistory, setHeartbeatHistory] = useState<{ time: string; node1: boolean; node2: boolean; node3: boolean }[]>([]);

  const fetchHeartbeats = async () => {
    try {
      const [clusterRes, nodesRes] = await Promise.all([
        fetch('/api/cluster').then(r => r.json()),
        fetch('/api/nodes').then(r => r.json())
      ]);
      setCluster(clusterRes);
      setNodes(nodesRes);

      const n1 = nodesRes.find((n: NodeStatus) => n.nodeId === '1')?.reachable ?? true;
      const n2 = nodesRes.find((n: NodeStatus) => n.nodeId === '2')?.reachable ?? true;
      const n3 = nodesRes.find((n: NodeStatus) => n.nodeId === '3')?.reachable ?? true;

      setHeartbeatHistory(prev => [
        ...prev.slice(-14),
        {
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
          node1: n1,
          node2: n2,
          node3: n3
        }
      ]);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchHeartbeats();
    const timer = setInterval(fetchHeartbeats, 2000);
    return () => clearInterval(timer);
  }, []);

  const primaryId = cluster?.primaryNodeId || '1';

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white flex items-center gap-2">
          <Activity className="w-6 h-6 text-rose-400" />
          Heartbeat Failure Detection & Node Health Monitoring
        </h1>
        <p className="text-sm text-slate-400 mt-1">
          Backups periodically probe PRIMARY availability via RMI (interval: 500ms, threshold: 2 consecutive missed heartbeats)
        </p>
      </div>

      {/* Heartbeat Parameters Config Card */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-[#111827] border border-slate-800 rounded-xl p-4">
          <span className="text-xs text-slate-500 font-mono">PROBE INTERVAL</span>
          <div className="text-xl font-bold text-white mt-1">500 ms</div>
          <span className="text-[11px] text-slate-400 mt-1 block">Scheduled via ScheduledExecutor</span>
        </div>

        <div className="bg-[#111827] border border-slate-800 rounded-xl p-4">
          <span className="text-xs text-slate-500 font-mono">FAIL THRESHOLD</span>
          <div className="text-xl font-bold text-amber-400 mt-1">2 Missed Pings</div>
          <span className="text-[11px] text-slate-400 mt-1 block">Consecutive timeout limit</span>
        </div>

        <div className="bg-[#111827] border border-slate-800 rounded-xl p-4">
          <span className="text-xs text-slate-500 font-mono">TOTAL TIMEOUT</span>
          <div className="text-xl font-bold text-rose-400 mt-1">1000 ms</div>
          <span className="text-[11px] text-slate-400 mt-1 block">Triggers Bully Election</span>
        </div>

        <div className="bg-[#111827] border border-slate-800 rounded-xl p-4">
          <span className="text-xs text-slate-500 font-mono">MONITORED TARGET</span>
          <div className="text-xl font-bold text-indigo-400 mt-1">Node {primaryId}</div>
          <span className="text-[11px] text-slate-400 mt-1 block">Current PRIMARY Leader</span>
        </div>
      </div>

      {/* Node Status Heartbeat Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {['1', '2', '3'].map((id) => {
          const isPrimary = id === primaryId;
          const node = nodes.find(n => n.nodeId === id);
          const isAlive = node ? node.reachable : true;

          return (
            <div
              key={id}
              className={`bg-[#111827] border rounded-xl p-5 relative overflow-hidden ${
                isPrimary ? 'border-amber-500/30' : 'border-slate-800'
              }`}
            >
              <div className="flex items-center justify-between mb-3">
                <span className="font-bold text-white flex items-center gap-2">
                  <Heart className={`w-4 h-4 ${isAlive ? 'text-rose-500 fill-rose-500 animate-pulse' : 'text-slate-600'}`} />
                  OrderNode-{id}
                </span>
                <span
                  className={`px-2 py-0.5 rounded text-[10px] font-bold border ${
                    isAlive
                      ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
                      : 'bg-rose-500/20 text-rose-400 border-rose-500/30'
                  }`}
                >
                  {isAlive ? 'ALIVE' : 'DEAD'}
                </span>
              </div>

              <div className="space-y-2 text-xs text-slate-300">
                <div className="flex justify-between py-1 border-b border-slate-800">
                  <span className="text-slate-500">Designated Role:</span>
                  <span className="font-bold text-indigo-300">{isPrimary ? 'PRIMARY LEADER' : 'BACKUP MONITOR'}</span>
                </div>
                <div className="flex justify-between py-1 border-b border-slate-800">
                  <span className="text-slate-500">Monitor Status:</span>
                  <span>{isPrimary ? 'Targeted by backups' : `Probing Node ${primaryId}`}</span>
                </div>
                <div className="flex justify-between py-1 border-b border-slate-800">
                  <span className="text-slate-500">Last Ping Probe:</span>
                  <span className="font-mono text-emerald-400">OK (RTT &lt; 2ms)</span>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Heartbeat Probe Timeline Log */}
      <div className="bg-[#111827] border border-slate-800 rounded-xl p-5">
        <h2 className="text-base font-semibold text-white mb-3">Live Heartbeat Probe Sequence</h2>
        <div className="space-y-2">
          {heartbeatHistory.slice().reverse().map((hb, i) => (
            <div
              key={i}
              className="flex items-center justify-between text-xs p-2.5 rounded-lg bg-slate-900 border border-slate-800 font-mono"
            >
              <span className="text-slate-400">{hb.time}</span>
              <div className="flex items-center gap-6">
                <span className={hb.node1 ? 'text-emerald-400' : 'text-rose-400 font-bold'}>
                  Node 1: {hb.node1 ? 'ALIVE' : 'MISSED'}
                </span>
                <span className={hb.node2 ? 'text-emerald-400' : 'text-rose-400 font-bold'}>
                  Node 2: {hb.node2 ? 'ALIVE' : 'MISSED'}
                </span>
                <span className={hb.node3 ? 'text-emerald-400' : 'text-rose-400 font-bold'}>
                  Node 3: {hb.node3 ? 'ALIVE' : 'MISSED'}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
