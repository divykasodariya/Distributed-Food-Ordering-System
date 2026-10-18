import React, { useEffect, useState } from 'react';
import {
  HardDrive,
  RefreshCw,
  FileText,
  RotateCcw,
  CheckCircle,
  Database,
  ArrowRight
} from 'lucide-react';
import type { NodeRecoveryLog } from '../types';

export const PersistenceRecoveryPage: React.FC = () => {
  const [logs, setLogs] = useState<NodeRecoveryLog[]>([]);
  const [selectedNode, setSelectedNode] = useState<string>('1');

  const fetchLogs = async () => {
    try {
      const res = await fetch('/api/recovery');
      const data = await res.json();
      setLogs(data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchLogs();
    const timer = setInterval(fetchLogs, 3000);
    return () => clearInterval(timer);
  }, []);

  const activeLog = logs.find(l => l.nodeId === selectedNode);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white flex items-center gap-2">
            <HardDrive className="w-6 h-6 text-indigo-400" />
            Disk Persistence, Event Sourcing & Crash Recovery
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Real disk logs (<code className="text-indigo-300 font-mono">data/nodeX/events.log</code>) with fsync() durability, state replay, and catch-up synchronization
          </p>
        </div>
        <button
          onClick={fetchLogs}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium border border-slate-700 transition"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Reload Disk Logs
        </button>
      </div>

      {/* Recovery Architecture Principles */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-[#111827] border border-slate-800 rounded-xl p-4">
          <div className="flex items-center gap-2 text-indigo-400 font-bold text-sm mb-1">
            <Database className="w-4 h-4" />
            Append-Only Disk Sourcing
          </div>
          <p className="text-xs text-slate-400 mt-2">
            Events are persisted synchronously using <span className="font-mono text-indigo-300">FileDescriptor.sync()</span> before acknowledgment, guaranteeing durability.
          </p>
        </div>

        <div className="bg-[#111827] border border-slate-800 rounded-xl p-4">
          <div className="flex items-center gap-2 text-emerald-400 font-bold text-sm mb-1">
            <RotateCcw className="w-4 h-4" />
            Startup State Replay
          </div>
          <p className="text-xs text-slate-400 mt-2">
            Upon process boot, <span className="font-mono text-emerald-300">rebuildStateFromLog()</span> replays all persisted events into ConcurrentHashMap OrderStore.
          </p>
        </div>

        <div className="bg-[#111827] border border-slate-800 rounded-xl p-4">
          <div className="flex items-center gap-2 text-amber-400 font-bold text-sm mb-1">
            <RefreshCw className="w-4 h-4" />
            Primary Catch-Up Sync
          </div>
          <p className="text-xs text-slate-400 mt-2">
            Rejoining backups contact the active Primary, fetch events newer than their local version, and quietly rejoin the cluster.
          </p>
        </div>
      </div>

      {/* Node Log File Selector & Raw Content Viewer */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Node Log Summary Cards */}
        <div className="space-y-4">
          {logs.map((log) => (
            <div
              key={log.nodeId}
              onClick={() => setSelectedNode(log.nodeId)}
              className={`p-4 rounded-xl border cursor-pointer transition text-xs ${
                selectedNode === log.nodeId
                  ? 'bg-indigo-950/40 border-indigo-500'
                  : 'bg-[#111827] border-slate-800 hover:border-slate-700'
              }`}
            >
              <div className="flex items-center justify-between mb-2">
                <span className="font-bold text-white text-sm flex items-center gap-2">
                  <FileText className="w-4 h-4 text-indigo-400" />
                  Node {log.nodeId} Disk Log
                </span>
                <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-slate-900 text-indigo-300 font-semibold border border-slate-800">
                  {log.exists ? 'SYNCED' : 'NO LOG'}
                </span>
              </div>
              <div className="text-slate-400 font-mono text-[11px] truncate">{log.filePath}</div>
              <div className="flex justify-between items-center mt-3 pt-2 border-t border-slate-800/80 text-slate-400 font-mono">
                <span>Total Events: <strong className="text-white">{log.totalEvents}</strong></span>
                <span>Version: <strong className="text-emerald-400">v{log.latestVersion}</strong></span>
              </div>
            </div>
          ))}
        </div>

        {/* Real Disk Log Viewer */}
        <div className="lg:col-span-2 bg-[#111827] border border-slate-800 rounded-xl p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between border-b border-slate-800 pb-3 mb-4">
              <div>
                <h2 className="text-base font-semibold text-white">
                  Event Log Viewer: Node {selectedNode}
                </h2>
                <p className="text-xs text-slate-400 font-mono mt-0.5">
                  Path: {activeLog?.filePath || 'N/A'}
                </p>
              </div>
              <span className="text-xs font-mono text-emerald-400 bg-emerald-950/60 px-2.5 py-1 rounded border border-emerald-800">
                fsync() Durable
              </span>
            </div>

            {/* Delimited Log Entries */}
            <div className="bg-slate-950 rounded-xl border border-slate-800/90 p-4 font-mono text-xs text-slate-300 max-h-[380px] overflow-y-auto space-y-2">
              {activeLog && activeLog.recentEntries.length > 0 ? (
                activeLog.recentEntries.map((line, idx) => (
                  <div key={idx} className="p-2 rounded bg-slate-900/60 border border-slate-800/50 break-all">
                    <span className="text-indigo-400 font-semibold">[{idx + 1}]</span> {line}
                  </div>
                ))
              ) : (
                <div className="text-slate-500 py-12 text-center">
                  No log entries recorded yet in data/node{selectedNode}/events.log
                </div>
              )}
            </div>
          </div>

          <div className="mt-4 p-3 bg-slate-950 rounded-lg border border-slate-800 text-[11px] text-slate-400">
            <strong>Format:</strong> <code className="text-indigo-300">version | eventId | orderId | eventType | sourceNode | lamport | physicalTime | customer | restaurant | item | qty | rider | timestamp</code>
          </div>
        </div>
      </div>
    </div>
  );
};
