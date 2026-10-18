import React, { useEffect, useState } from 'react';
import {
  Crown,
  Play,
  RotateCcw,
  AlertTriangle,
  CheckCircle,
  ShieldAlert,
  ArrowRight,
  Server
} from 'lucide-react';
import type { ClusterInfo, NodeStatus } from '../types';
import { useLiveStream } from '../hooks/useLiveStream';

export const BullyElectionPage: React.FC = () => {
  const [cluster, setCluster] = useState<ClusterInfo | null>(null);
  const [nodes, setNodes] = useState<NodeStatus[]>([]);
  const [initiatingNode, setInitiatingNode] = useState<string>('2');
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [executing, setExecuting] = useState<boolean>(false);
  const { events } = useLiveStream();

  const fetchStatus = async () => {
    try {
      const [clusterRes, nodesRes] = await Promise.all([
        fetch('/api/cluster').then(r => r.json()),
        fetch('/api/nodes').then(r => r.json())
      ]);
      setCluster(clusterRes);
      setNodes(nodesRes);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchStatus();
    const timer = setInterval(fetchStatus, 3000);
    return () => clearInterval(timer);
  }, []);

  const triggerElection = async () => {
    setExecuting(true);
    setActionMessage(null);
    try {
      const res = await fetch('/api/admin/start-election', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nodeId: initiatingNode })
      });
      const data = await res.json();
      setActionMessage(`Election process initiated from Node ${initiatingNode}! Highest responsive ID wins and announces COORDINATOR.`);
      fetchStatus();
    } catch (err) {
      setActionMessage('Failed to trigger election: ' + err);
    } finally {
      setExecuting(false);
    }
  };

  const primaryId = cluster?.primaryNodeId || '1';

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white flex items-center gap-2">
          <Crown className="w-6 h-6 text-amber-400" />
          Bully Leader Election & Dynamic Gateway Failover
        </h1>
        <p className="text-sm text-slate-400 mt-1">
          Classic Bully Algorithm: live node with highest numeric ID always bullies lower nodes and becomes PRIMARY coordinator
        </p>
      </div>

      {/* Algorithm Rules */}
      <div className="bg-[#111827] border border-slate-800 rounded-xl p-5">
        <h2 className="text-sm font-semibold text-white uppercase tracking-wider mb-3">
          Bully Protocol Rules Implemented in BullyElectionManager.java
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 text-xs">
          <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
            <span className="font-bold text-indigo-400 block">1. Challenge Higher</span>
            <p className="text-slate-400 mt-1">When primary fails, candidate sends ELECTION challenge to all peers with higher numeric IDs.</p>
          </div>
          <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
            <span className="font-bold text-indigo-400 block">2. Higher Node Yields</span>
            <p className="text-slate-400 mt-1">If any higher peer responds OK, caller yields and waits for COORDINATOR announcement.</p>
          </div>
          <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
            <span className="font-bold text-amber-400 block">3. Victory Declaration</span>
            <p className="text-slate-400 mt-1">If no higher peer answers, candidate declares victory, assumes PRIMARY, and broadcasts COORDINATOR.</p>
          </div>
          <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
            <span className="font-bold text-emerald-400 block">4. Gateway Update</span>
            <p className="text-slate-400 mt-1">New leader invokes notifyLeaderChanged() on GatewayServer to update routing table dynamically.</p>
          </div>
        </div>
      </div>

      {/* Cluster Nodes & Priority Hierarchy */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Nodes Hierarchy Canvas */}
        <div className="lg:col-span-2 bg-[#111827] border border-slate-800 rounded-xl p-5">
          <h2 className="text-base font-semibold text-white mb-4">Cluster Hierarchy & Priority Ordering</h2>

          <div className="space-y-4">
            {[
              { id: '3', port: 1103, priority: 'HIGHEST (Priority 3)' },
              { id: '2', port: 1102, priority: 'MEDIUM (Priority 2)' },
              { id: '1', port: 1101, priority: 'LOWEST (Priority 1)' }
            ].map(({ id, port, priority }) => {
              const isPrimary = id === primaryId;
              const nodeStatus = nodes.find(n => n.nodeId === id);
              const isReachable = nodeStatus ? nodeStatus.reachable : true;

              return (
                <div
                  key={id}
                  className={`p-4 rounded-xl border flex items-center justify-between transition-all ${
                    isPrimary
                      ? 'bg-amber-950/20 border-amber-500/50 shadow-md shadow-amber-500/10'
                      : 'bg-slate-900 border-slate-800'
                  }`}
                >
                  <div className="flex items-center gap-4">
                    <div className={`p-3 rounded-xl ${isPrimary ? 'bg-amber-500/20 text-amber-400' : 'bg-slate-800 text-slate-400'}`}>
                      {isPrimary ? <Crown className="w-6 h-6" /> : <Server className="w-6 h-6" />}
                    </div>

                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-white text-sm">OrderNode-{id}</span>
                        {isPrimary && (
                          <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40">
                            ELECTED LEADER
                          </span>
                        )}
                      </div>
                      <span className="text-xs text-slate-400 font-mono mt-0.5 block">
                        Port :{port} • {priority}
                      </span>
                    </div>
                  </div>

                  <div className="text-right">
                    <span
                      className={`px-2.5 py-1 rounded text-xs font-bold border ${
                        isReachable
                          ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
                          : 'bg-rose-500/20 text-rose-400 border-rose-500/30'
                      }`}
                    >
                      {isReachable ? 'ALIVE' : 'FAILED / DOWN'}
                    </span>
                    <div className="text-[11px] font-mono text-slate-500 mt-1">
                      Role: {isPrimary ? 'PRIMARY' : 'BACKUP'}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Interactive Election Trigger Console */}
        <div className="bg-[#111827] border border-slate-800 rounded-xl p-5 flex flex-col justify-between">
          <div>
            <h2 className="text-base font-semibold text-white mb-2">Simulate Election Challenge</h2>
            <p className="text-xs text-slate-400 mb-4">
              Trigger a live Bully election call on the specified node. Observe message exchange and coordinator announcement.
            </p>

            <div className="space-y-4 text-xs">
              <div>
                <label className="text-slate-300 font-medium block mb-1">Initiating Candidate Node:</label>
                <select
                  value={initiatingNode}
                  onChange={(e) => setInitiatingNode(e.target.value)}
                  className="w-full p-2.5 rounded-lg bg-slate-900 border border-slate-700 text-white font-mono"
                >
                  <option value="2">Node 2 (Challenges Node 3)</option>
                  <option value="1">Node 1 (Challenges Node 2 & 3)</option>
                  <option value="3">Node 3 (Highest - Instant Victory)</option>
                </select>
              </div>

              <button
                onClick={triggerElection}
                disabled={executing}
                className="w-full py-2.5 px-4 bg-amber-600 hover:bg-amber-500 disabled:bg-amber-900 text-slate-950 font-bold text-xs rounded-lg transition flex items-center justify-center gap-2 shadow"
              >
                <Play className="w-4 h-4 fill-slate-950" />
                {executing ? 'Initiating Election...' : 'Start Bully Election'}
              </button>

              {actionMessage && (
                <div className="p-3 bg-amber-950/40 border border-amber-800/40 rounded-lg text-amber-200 text-xs">
                  {actionMessage}
                </div>
              )}
            </div>
          </div>

          <div className="mt-6 p-3 bg-slate-950 rounded-lg border border-slate-800 text-[11px] text-slate-400">
            <strong>Gateway Dynamic Failover:</strong> When a new leader wins, Gateway routes are instantly cleared and re-routed to the new Primary without needing client reconfiguration.
          </div>
        </div>
      </div>
    </div>
  );
};
