import React, { useEffect, useState } from 'react';
import {
  Network,
  Server,
  User,
  Bike,
  Crown,
  ShieldCheck,
  RefreshCw,
  Info,
  ArrowRight,
  Activity
} from 'lucide-react';
import type { ClusterInfo, NodeStatus } from '../types';
import { useLiveStream } from '../hooks/useLiveStream';

export const TopologyPage: React.FC = () => {
  const [cluster, setCluster] = useState<ClusterInfo | null>(null);
  const [nodes, setNodes] = useState<NodeStatus[]>([]);
  const [selectedNode, setSelectedNode] = useState<string | null>('gateway');
  const { lastEvent } = useLiveStream();

  const fetchTopology = async () => {
    try {
      const [clusterRes, nodesRes] = await Promise.all([
        fetch('/api/cluster').then(r => r.json()),
        fetch('/api/nodes').then(r => r.json())
      ]);
      setCluster(clusterRes);
      setNodes(nodesRes);
    } catch (err) {
      console.error('Failed fetching topology:', err);
    }
  };

  useEffect(() => {
    fetchTopology();
    const interval = setInterval(fetchTopology, 3000);
    return () => clearInterval(interval);
  }, []);

  const primaryId = cluster?.primaryNodeId || '1';

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white flex items-center gap-2">
            <Network className="w-6 h-6 text-indigo-400" />
            Interactive Cluster Topology & RPC Visualization
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Real communication paths between Customer Client, Rider Client, Gateway, Primary Leader, and Backup Replicas
          </p>
        </div>
        <button
          onClick={fetchTopology}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium border border-slate-700 transition"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Refresh Nodes
        </button>
      </div>

      {/* Main Grid: SVG Canvas + Detail Sidebar */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Topology Interactive Canvas */}
        <div className="lg:col-span-2 bg-[#111827] border border-slate-800 rounded-xl p-6 relative flex flex-col justify-between min-h-[500px] overflow-hidden">
          {/* Legend Banner */}
          <div className="flex items-center justify-between border-b border-slate-800 pb-3 text-xs text-slate-400">
            <div className="flex items-center gap-4">
              <span className="flex items-center gap-1.5">
                <span className="w-3 h-3 rounded-full bg-indigo-500"></span> Gateway
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-3 h-3 rounded-full bg-amber-500"></span> Primary Leader
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-3 h-3 rounded-full bg-emerald-500"></span> Backup Node
              </span>
            </div>
            <div className="text-[11px] text-slate-500 font-mono">
              Live RMI Communication Graph
            </div>
          </div>

          {/* SVG Visual Graph */}
          <div className="flex-1 flex items-center justify-center my-6">
            <div className="relative w-full max-w-2xl h-[380px] bg-slate-950/60 rounded-xl border border-slate-800/80 p-6 flex flex-col justify-between">
              {/* Clients Layer */}
              <div className="flex justify-around items-center">
                {/* Customer Node */}
                <div
                  onClick={() => setSelectedNode('customer')}
                  className={`cursor-pointer flex flex-col items-center p-3 rounded-xl border transition-all ${
                    selectedNode === 'customer'
                      ? 'bg-sky-950/40 border-sky-500 shadow-md shadow-sky-500/10'
                      : 'bg-slate-900 border-slate-800 hover:border-slate-700'
                  }`}
                >
                  <div className="p-2.5 bg-sky-500/20 text-sky-400 rounded-lg mb-1">
                    <User className="w-5 h-5" />
                  </div>
                  <span className="text-xs font-bold text-white">Customer Client</span>
                  <span className="text-[10px] text-slate-400 font-mono">CLI / Web Browser</span>
                </div>

                {/* Rider Node */}
                <div
                  onClick={() => setSelectedNode('rider')}
                  className={`cursor-pointer flex flex-col items-center p-3 rounded-xl border transition-all ${
                    selectedNode === 'rider'
                      ? 'bg-purple-950/40 border-purple-500 shadow-md shadow-purple-500/10'
                      : 'bg-slate-900 border-slate-800 hover:border-slate-700'
                  }`}
                >
                  <div className="p-2.5 bg-purple-500/20 text-purple-400 rounded-lg mb-1">
                    <Bike className="w-5 h-5" />
                  </div>
                  <span className="text-xs font-bold text-white">Rider Client</span>
                  <span className="text-[10px] text-slate-400 font-mono">R1, R2 Bidding</span>
                </div>
              </div>

              {/* Gateway Server Layer */}
              <div className="flex justify-center items-center my-2">
                <div
                  onClick={() => setSelectedNode('gateway')}
                  className={`cursor-pointer flex items-center gap-3 px-5 py-3 rounded-xl border transition-all ${
                    selectedNode === 'gateway'
                      ? 'bg-indigo-950/50 border-indigo-500 shadow-lg shadow-indigo-500/20'
                      : 'bg-slate-900 border-slate-800 hover:border-slate-700'
                  }`}
                >
                  <div className="p-2.5 bg-indigo-500/20 text-indigo-400 rounded-lg">
                    <Server className="w-6 h-6" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-white">Gateway Server</span>
                      <span className="px-1.5 py-0.5 rounded text-[10px] bg-emerald-500/20 text-emerald-400 font-bold border border-emerald-500/30">
                        1099
                      </span>
                    </div>
                    <span className="text-[11px] text-slate-400">Routes Writes to Primary; Balances Reads (WRR)</span>
                  </div>
                </div>
              </div>

              {/* Backend Order Nodes Layer */}
              <div className="flex justify-between items-center gap-4">
                {['1', '2', '3'].map((id) => {
                  const isPrimary = id === primaryId;
                  const nodeStatus = nodes.find(n => n.nodeId === id);
                  const isReachable = nodeStatus ? nodeStatus.reachable : true;
                  const port = 1100 + parseInt(id);

                  return (
                    <div
                      key={id}
                      onClick={() => setSelectedNode(id)}
                      className={`flex-1 cursor-pointer flex flex-col items-center p-3 rounded-xl border transition-all relative ${
                        selectedNode === id
                          ? isPrimary
                            ? 'bg-amber-950/40 border-amber-500 shadow-lg shadow-amber-500/20'
                            : 'bg-emerald-950/40 border-emerald-500 shadow-lg shadow-emerald-500/20'
                          : 'bg-slate-900 border-slate-800 hover:border-slate-700'
                      }`}
                    >
                      {/* Leader Crown Badge */}
                      {isPrimary && (
                        <div className="absolute -top-3 px-2 py-0.5 rounded-full bg-amber-500 text-slate-950 text-[10px] font-bold flex items-center gap-1 shadow">
                          <Crown className="w-3 h-3" />
                          PRIMARY
                        </div>
                      )}

                      <div
                        className={`p-2.5 rounded-lg mb-1.5 ${
                          isPrimary
                            ? 'bg-amber-500/20 text-amber-400'
                            : 'bg-emerald-500/20 text-emerald-400'
                        }`}
                      >
                        <Server className="w-5 h-5" />
                      </div>
                      <span className="text-xs font-bold text-white">OrderNode-{id}</span>
                      <span className="text-[10px] font-mono text-slate-400">Port :{port}</span>
                      <span
                        className={`mt-1 text-[9px] px-1.5 py-0.5 rounded font-bold border ${
                          isReachable
                            ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
                            : 'bg-rose-500/20 text-rose-400 border-rose-500/30'
                        }`}
                      >
                        {isReachable ? 'ALIVE' : 'UNREACHABLE'}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Active Live Event Pulse Banner */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-lg p-3 text-xs flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping"></span>
              <span className="text-slate-400">Last Telemetry:</span>
              <span className="text-slate-200 font-medium">
                {lastEvent ? `${lastEvent.sourceNode} → ${lastEvent.targetNode || 'CLUSTER'}: ${lastEvent.details}` : 'Cluster running stably'}
              </span>
            </div>
            {lastEvent && (
              <span className="text-[10px] font-mono text-indigo-400 bg-indigo-950/60 px-2 py-0.5 rounded border border-indigo-800">
                L:{lastEvent.lamportTime}
              </span>
            )}
          </div>
        </div>

        {/* Selected Node Details Sidebar */}
        <div className="bg-[#111827] border border-slate-800 rounded-xl p-5 flex flex-col justify-between">
          <div>
            <h2 className="text-base font-semibold text-white flex items-center gap-2 border-b border-slate-800 pb-3">
              <Info className="w-4 h-4 text-indigo-400" />
              Node Inspection Panel
            </h2>

            {selectedNode === 'gateway' && (
              <div className="mt-4 space-y-3 text-xs">
                <div className="p-3 bg-indigo-950/30 border border-indigo-800/40 rounded-lg">
                  <div className="font-bold text-indigo-300 text-sm">Gateway Server (RMI 1099)</div>
                  <p className="text-slate-400 mt-1">Single entry point for client requests. Serves as Cristian physical time authority.</p>
                </div>
                <div className="space-y-2 text-slate-300">
                  <div className="flex justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-500">Service:</span>
                    <span className="font-mono text-indigo-300">GatewayService</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-500">Active Leader:</span>
                    <span className="font-bold text-amber-400">Node {primaryId} (PRIMARY)</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-500">Read Load Balancer:</span>
                    <span className="text-emerald-400">Smooth WRR (1:3:3)</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-500">Failover Mode:</span>
                    <span className="text-slate-300">Dynamic routing on Coordinator broadcast</span>
                  </div>
                </div>
              </div>
            )}

            {['1', '2', '3'].includes(selectedNode || '') && (
              <div className="mt-4 space-y-3 text-xs">
                {(() => {
                  const node = nodes.find(n => n.nodeId === selectedNode);
                  const isPrimary = selectedNode === primaryId;

                  return (
                    <>
                      <div className={`p-3 rounded-lg border ${
                        isPrimary
                          ? 'bg-amber-950/30 border-amber-800/40 text-amber-300'
                          : 'bg-emerald-950/30 border-emerald-800/40 text-emerald-300'
                      }`}>
                        <div className="font-bold text-sm flex items-center justify-between">
                          <span>OrderNode {selectedNode}</span>
                          <span className="text-xs px-2 py-0.5 rounded bg-slate-900 font-mono">
                            {isPrimary ? 'PRIMARY' : 'BACKUP'}
                          </span>
                        </div>
                        <p className="text-slate-400 mt-1">
                          {isPrimary
                            ? 'Handles all writes, appends disk log, replicates events to backups.'
                            : 'Receives replicated events, serves read queries with WRR weight 3.'}
                        </p>
                      </div>

                      <div className="space-y-2 text-slate-300">
                        <div className="flex justify-between py-1 border-b border-slate-800">
                          <span className="text-slate-500">RMI Endpoint:</span>
                          <span className="font-mono text-indigo-300">rmi://localhost:{1100 + parseInt(selectedNode || '1')}/OrderNode{selectedNode}</span>
                        </div>
                        <div className="flex justify-between py-1 border-b border-slate-800">
                          <span className="text-slate-500">Log Version:</span>
                          <span className="font-mono text-emerald-400 font-semibold">v{node?.latestLogVersion || 0}</span>
                        </div>
                        <div className="flex justify-between py-1 border-b border-slate-800">
                          <span className="text-slate-500">Disk Log:</span>
                          <span className="font-mono text-slate-400">data/node{selectedNode}/events.log</span>
                        </div>
                        <div className="flex justify-between py-1 border-b border-slate-800">
                          <span className="text-slate-500">Bully Priority:</span>
                          <span className="font-mono text-white font-bold">{selectedNode} / 3</span>
                        </div>
                      </div>
                    </>
                  );
                })()}
              </div>
            )}

            {(selectedNode === 'customer' || selectedNode === 'rider') && (
              <div className="mt-4 space-y-3 text-xs">
                <div className="p-3 bg-slate-900 border border-slate-800 rounded-lg">
                  <div className="font-bold text-white text-sm">
                    {selectedNode === 'customer' ? 'Customer Client' : 'Rider Client'}
                  </div>
                  <p className="text-slate-400 mt-1">
                    {selectedNode === 'customer'
                      ? 'Communicates with Gateway over Java RMI to browse menus and place orders.'
                      : 'Synchronizes physical clock via Cristian algorithm and bids on delivery opportunities.'}
                  </p>
                </div>
              </div>
            )}
          </div>

          <div className="p-3 bg-slate-950 rounded-lg border border-slate-800/80 text-[11px] text-slate-400">
            Click any node in the topology canvas to inspect its role, port, RMI URL, and log persistence status.
          </div>
        </div>
      </div>
    </div>
  );
};
