import React, { useState } from 'react';
import {
  Timer,
  Play,
  RotateCcw,
  Zap,
  Info,
  CheckCircle,
  Clock,
  ArrowRight
} from 'lucide-react';
import type { CristianSyncResult } from '../types';

export const CristianSyncPage: React.FC = () => {
  const [networkDelay, setNetworkDelay] = useState<number>(50);
  const [syncResult, setSyncResult] = useState<CristianSyncResult | null>(null);
  const [syncHistory, setSyncHistory] = useState<CristianSyncResult[]>([]);
  const [syncing, setSyncing] = useState<boolean>(false);

  const runCristianSync = async () => {
    setSyncing(true);
    try {
      const res = await fetch('/api/admin/sync-clock', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ delayMs: networkDelay.toString() })
      });
      const data: CristianSyncResult = await res.json();
      setSyncResult(data);
      setSyncHistory(prev => [data, ...prev.slice(0, 9)]);
    } catch (err) {
      console.error('Failed to run Cristian sync:', err);
    } finally {
      setSyncing(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white flex items-center gap-2">
          <Timer className="w-6 h-6 text-indigo-400" />
          Cristian's Physical Clock Synchronization
        </h1>
        <p className="text-sm text-slate-400 mt-1">
          Synchronizing physical timestamps between Rider Clients and Gateway Time Server with Round-Trip Time (RTT) adjustment
        </p>
      </div>

      {/* Mathematical Principle Card */}
      <div className="bg-[#111827] border border-slate-800 rounded-xl p-5">
        <h2 className="text-sm font-semibold text-white uppercase tracking-wider mb-3">
          Algorithmic Formulation
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 text-xs font-mono">
          <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
            <span className="text-slate-500 block text-[10px]">ROUND-TRIP TIME</span>
            <span className="text-indigo-400 font-bold text-sm block mt-1">RTT = t1 - t0</span>
            <span className="text-slate-400 text-[11px] mt-1 block">Full request-reply interval</span>
          </div>

          <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
            <span className="text-slate-500 block text-[10px]">ONE-WAY DELAY</span>
            <span className="text-indigo-400 font-bold text-sm block mt-1">delay ≈ RTT / 2</span>
            <span className="text-slate-400 text-[11px] mt-1 block">Assumes symmetric latency</span>
          </div>

          <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
            <span className="text-slate-500 block text-[10px]">ESTIMATED SERVER TIME</span>
            <span className="text-emerald-400 font-bold text-sm block mt-1">T_server + RTT/2</span>
            <span className="text-slate-400 text-[11px] mt-1 block">Projected time at arrival t1</span>
          </div>

          <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
            <span className="text-slate-500 block text-[10px]">CLOCK OFFSET</span>
            <span className="text-amber-400 font-bold text-sm block mt-1">θ = Ts - (t0 + t1)/2</span>
            <span className="text-slate-400 text-[11px] mt-1 block">Applied to client action bids</span>
          </div>
        </div>
      </div>

      {/* Interactive Experiment Console */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Control Box */}
        <div className="bg-[#111827] border border-slate-800 rounded-xl p-5 flex flex-col justify-between">
          <div>
            <h2 className="text-base font-semibold text-white mb-2">Simulate Network Delay & Sync</h2>
            <p className="text-xs text-slate-400 mb-4">
              Inject controlled transit delay and measure Cristian's RTT adjustment against the live Gateway.
            </p>

            <div className="space-y-4">
              <div>
                <div className="flex justify-between text-xs text-slate-300 mb-1">
                  <span>Injected Latency:</span>
                  <span className="font-mono font-bold text-indigo-400">{networkDelay} ms</span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="500"
                  step="10"
                  value={networkDelay}
                  onChange={(e) => setNetworkDelay(parseInt(e.target.value))}
                  className="w-full accent-indigo-500 cursor-pointer"
                />
              </div>

              <button
                onClick={runCristianSync}
                disabled={syncing}
                className="w-full py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 disabled:bg-indigo-900 text-white font-medium text-xs rounded-lg transition flex items-center justify-center gap-2 shadow"
              >
                <Play className="w-4 h-4 fill-white" />
                {syncing ? 'Synchronizing over RMI...' : "Execute Cristian's Algorithm"}
              </button>
            </div>
          </div>

          <div className="mt-6 p-3 bg-slate-950 rounded-lg border border-slate-800 text-[11px] text-slate-400">
            <strong>Note on Assumptions:</strong> Accuracy is bounded within <span className="font-mono text-indigo-300">±(RTT/2)</span>. Cristian's algorithm does not guarantee monotonic physical time across process crashes.
          </div>
        </div>

        {/* Real Measurements Display */}
        <div className="lg:col-span-2 bg-[#111827] border border-slate-800 rounded-xl p-5">
          <h2 className="text-base font-semibold text-white mb-3">Measurement & Calculation Results</h2>

          {syncResult ? (
            <div className="space-y-4 text-xs">
              <div className="grid grid-cols-2 md:grid-cols-3 gap-3 font-mono">
                <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
                  <span className="text-slate-500 text-[10px] block">CLIENT TRANSMIT (t0)</span>
                  <span className="text-white font-bold block mt-1">{syncResult.t0}</span>
                  <span className="text-[10px] text-slate-400">{new Date(syncResult.t0).toLocaleTimeString()}</span>
                </div>

                <div className="p-3 bg-indigo-950/40 rounded-lg border border-indigo-800/40">
                  <span className="text-indigo-400 text-[10px] block font-bold">GATEWAY SERVER TIME (Ts)</span>
                  <span className="text-indigo-300 font-bold block mt-1">{syncResult.serverTime}</span>
                  <span className="text-[10px] text-indigo-400/80">{new Date(syncResult.serverTime).toLocaleTimeString()}</span>
                </div>

                <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
                  <span className="text-slate-500 text-[10px] block">CLIENT RECEIVE (t1)</span>
                  <span className="text-white font-bold block mt-1">{syncResult.t1}</span>
                  <span className="text-[10px] text-slate-400">{new Date(syncResult.t1).toLocaleTimeString()}</span>
                </div>

                <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
                  <span className="text-slate-500 text-[10px] block">ROUND-TRIP TIME (RTT)</span>
                  <span className="text-amber-400 font-bold text-sm block mt-1">{syncResult.rtt} ms</span>
                </div>

                <div className="p-3 bg-slate-900 rounded-lg border border-slate-800">
                  <span className="text-slate-500 text-[10px] block">ESTIMATED 1-WAY DELAY</span>
                  <span className="text-amber-400 font-bold text-sm block mt-1">{syncResult.oneWayDelay} ms</span>
                </div>

                <div className="p-3 bg-emerald-950/40 rounded-lg border border-emerald-800/40">
                  <span className="text-emerald-400 text-[10px] block font-bold">CALCULATED CLOCK OFFSET</span>
                  <span className="text-emerald-300 font-bold text-sm block mt-1">{syncResult.offset >= 0 ? `+${syncResult.offset}` : syncResult.offset} ms</span>
                </div>
              </div>

              {/* Synchronized Physical Clock Preview */}
              <div className="p-4 bg-slate-900 rounded-xl border border-slate-800 flex items-center justify-between">
                <div>
                  <span className="text-xs text-slate-400 block">Synchronized Physical Time for Next Rider Bid:</span>
                  <span className="text-base font-bold text-white font-mono mt-0.5 block">
                    {new Date(syncResult.synchronizedTime).toLocaleTimeString()}
                  </span>
                </div>
                <div className="px-3 py-1 bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 rounded-lg font-bold text-xs flex items-center gap-1.5">
                  <CheckCircle className="w-4 h-4" />
                  Synchronized
                </div>
              </div>
            </div>
          ) : (
            <div className="text-center py-16 text-slate-500 text-xs">
              Click "Execute Cristian's Algorithm" to run the real physical clock synchronization probe.
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
