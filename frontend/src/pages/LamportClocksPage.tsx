import React, { useEffect, useState } from 'react';
import {
  Clock,
  ArrowRight,
  PlusCircle,
  HelpCircle,
  ShieldCheck,
  Send,
  Zap,
  TrendingUp
} from 'lucide-react';
import type { OrderSnapshot } from '../types';
import { useLiveStream } from '../hooks/useLiveStream';

export const LamportClocksPage: React.FC = () => {
  const [orders, setOrders] = useState<OrderSnapshot[]>([]);
  const { events } = useLiveStream();

  useEffect(() => {
    fetch('/api/orders')
      .then(r => r.json())
      .then(setOrders)
      .catch(console.error);
  }, [events]);

  // Gather all events sorted by Lamport timestamp
  const allEvents = orders
    .flatMap(o => o.eventLog)
    .sort((a, b) => a.lamportTimestamp - b.lamportTimestamp);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white flex items-center gap-2">
          <Clock className="w-6 h-6 text-indigo-400" />
          Lamport Logical Clock Laboratory
        </h1>
        <p className="text-sm text-slate-400 mt-1">
          Tracking distributed causal event history ($A \to B \implies L(A) &lt; L(B)$) across Gateway, Nodes, Customers, and Riders
        </p>
      </div>

      {/* Rules Explanatory Card */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-[#111827] border border-slate-800 rounded-xl p-4">
          <div className="flex items-center gap-2 text-indigo-400 font-bold text-sm mb-1">
            <span className="p-1.5 bg-indigo-500/20 rounded-md">1</span>
            Local Event Rule
          </div>
          <p className="text-xs text-slate-400 mt-2">
            Before executing an internal action, the process increments its logical clock:
          </p>
          <div className="mt-2 font-mono text-xs bg-slate-900 p-2 rounded text-indigo-300 font-semibold">
            L = L + 1
          </div>
        </div>

        <div className="bg-[#111827] border border-slate-800 rounded-xl p-4">
          <div className="flex items-center gap-2 text-indigo-400 font-bold text-sm mb-1">
            <span className="p-1.5 bg-indigo-500/20 rounded-md">2</span>
            Send Event Rule
          </div>
          <p className="text-xs text-slate-400 mt-2">
            Before dispatching a distributed RPC, increment and attach timestamp to message:
          </p>
          <div className="mt-2 font-mono text-xs bg-slate-900 p-2 rounded text-indigo-300 font-semibold">
            L = L + 1; send(msg, L)
          </div>
        </div>

        <div className="bg-[#111827] border border-slate-800 rounded-xl p-4">
          <div className="flex items-center gap-2 text-emerald-400 font-bold text-sm mb-1">
            <span className="p-1.5 bg-emerald-500/20 rounded-md">3</span>
            Receive Event Rule
          </div>
          <p className="text-xs text-slate-400 mt-2">
            Upon receiving an incoming message carrying timestamp L_msg, update clock:
          </p>
          <div className="mt-2 font-mono text-xs bg-slate-900 p-2 rounded text-emerald-400 font-semibold">
            L = max(L, L_msg) + 1
          </div>
        </div>
      </div>

      {/* Multi-lane Timeline */}
      <div className="bg-[#111827] border border-slate-800 rounded-xl p-5">
        <div className="flex items-center justify-between mb-4 border-b border-slate-800 pb-3">
          <div>
            <h2 className="text-base font-semibold text-white">Multi-Lane Distributed Event Sequence</h2>
            <p className="text-xs text-slate-400">
              Each lane represents an independent process tracking its logical clock progression
            </p>
          </div>
          <div className="text-xs font-mono text-slate-400">
            Total Captured Events: <strong className="text-indigo-400">{allEvents.length}</strong>
          </div>
        </div>

        {allEvents.length === 0 ? (
          <div className="text-center py-12 text-slate-500 text-xs">
            No events recorded yet. Create an order or submit a rider bid to populate the Lamport timeline.
          </div>
        ) : (
          <div className="space-y-3">
            {allEvents.map((evt, idx) => (
              <div
                key={evt.eventId || idx}
                className="p-3 bg-slate-900 rounded-lg border border-slate-800/80 flex items-center justify-between text-xs"
              >
                <div className="flex items-center gap-4">
                  <div className="w-12 h-10 rounded bg-indigo-950/80 border border-indigo-800 text-indigo-300 font-mono font-bold flex flex-col items-center justify-center">
                    <span className="text-[10px] text-slate-500">TICK</span>
                    <span>{evt.lamportTimestamp}</span>
                  </div>

                  <div>
                    <div className="font-semibold text-white flex items-center gap-2">
                      <span>{evt.eventType}</span>
                      <span className="text-[10px] text-slate-400 font-mono">Order #{evt.orderId}</span>
                    </div>
                    <div className="text-slate-400 text-[11px] mt-0.5">
                      Process / Source: <strong className="text-indigo-300">{evt.sourceId}</strong>
                    </div>
                  </div>
                </div>

                <div className="text-right font-mono text-[11px] text-slate-500">
                  {evt.physicalTimestamp ? (
                    <div>Physical: {new Date(evt.physicalTimestamp).toLocaleTimeString()}</div>
                  ) : (
                    <div className="italic">Causal Sequence Point</div>
                  )}
                  <span className="text-emerald-400 font-medium">L={evt.lamportTimestamp}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
