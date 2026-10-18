import React, { useEffect, useState } from 'react';
import {
  Share2,
  ArrowRight,
  CheckCircle,
  Database,
  Layers,
  Clock,
  ShieldCheck,
  Send,
  AlertCircle
} from 'lucide-react';
import type { OrderSnapshot, NodeStatus } from '../types';
import { useLiveStream } from '../hooks/useLiveStream';

export const RmiReplicationPage: React.FC = () => {
  const [orders, setOrders] = useState<OrderSnapshot[]>([]);
  const [nodes, setNodes] = useState<NodeStatus[]>([]);
  const [selectedOrder, setSelectedOrder] = useState<OrderSnapshot | null>(null);
  const { events } = useLiveStream();

  const fetchData = async () => {
    try {
      const [ordersRes, nodesRes] = await Promise.all([
        fetch('/api/orders').then(r => r.json()),
        fetch('/api/nodes').then(r => r.json())
      ]);
      setOrders(ordersRes);
      setNodes(nodesRes);
      if (ordersRes.length > 0 && !selectedOrder) {
        setSelectedOrder(ordersRes[0]);
      }
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchData();
    const timer = setInterval(fetchData, 3000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white flex items-center gap-2">
          <Share2 className="w-6 h-6 text-indigo-400" />
          Java RMI Communication & Application-State Replication
        </h1>
        <p className="text-sm text-slate-400 mt-1">
          Inspect order lifecycle sequences: Customer → Gateway → Primary Leader → Synchronous Backup Replication → Explicit ACKs
        </p>
      </div>

      {/* RMI Replication Architecture Walkthrough */}
      <div className="bg-[#111827] border border-slate-800 rounded-xl p-5">
        <h2 className="text-sm font-semibold text-white uppercase tracking-wider mb-4 flex items-center gap-2">
          <Layers className="w-4 h-4 text-indigo-400" />
          Live State Machine Replication Flow
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-5 gap-3 text-xs">
          <div className="p-3 bg-slate-900 border border-slate-800 rounded-lg flex flex-col justify-between">
            <span className="font-bold text-indigo-400">1. Client RMI Call</span>
            <p className="text-slate-400 mt-1">Customer / Rider invokes GatewayRemote over port 1099 with client Lamport timestamp.</p>
            <span className="font-mono text-[10px] text-slate-500 mt-2">t_send, L_client</span>
          </div>

          <div className="p-3 bg-slate-900 border border-slate-800 rounded-lg flex flex-col justify-between">
            <span className="font-bold text-indigo-400">2. Gateway Dispatch</span>
            <p className="text-slate-400 mt-1">Gateway advances its Lamport clock (L = max + 1) and routes write to designated PRIMARY node.</p>
            <span className="font-mono text-[10px] text-slate-500 mt-2">GatewayServer.java</span>
          </div>

          <div className="p-3 bg-amber-950/20 border border-amber-800/40 rounded-lg flex flex-col justify-between">
            <span className="font-bold text-amber-400">3. Primary Event Append</span>
            <p className="text-slate-400 mt-1">Primary generates monotonic version & eventId, updates OrderStore, and syncs to disk (events.log).</p>
            <span className="font-mono text-[10px] text-amber-400/70 mt-2">fsync() durability</span>
          </div>

          <div className="p-3 bg-slate-900 border border-slate-800 rounded-lg flex flex-col justify-between">
            <span className="font-bold text-emerald-400">4. Backup Broadcast</span>
            <p className="text-slate-400 mt-1">ReplicationManager broadcasts ReplicationEvent over RMI stub to all live BACKUP nodes.</p>
            <span className="font-mono text-[10px] text-slate-500 mt-2">replicateEvent()</span>
          </div>

          <div className="p-3 bg-slate-900 border border-slate-800 rounded-lg flex flex-col justify-between">
            <span className="font-bold text-emerald-400">5. Explicit ACK</span>
            <p className="text-slate-400 mt-1">Backups apply event idempotently, flush to their own log, and return ReplicationAck to Primary.</p>
            <span className="font-mono text-[10px] text-slate-500 mt-2">ReplicationAck(Applied)</span>
          </div>
        </div>
      </div>

      {/* Select Order & Sequence Inspector */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Orders List */}
        <div className="bg-[#111827] border border-slate-800 rounded-xl p-5">
          <h2 className="text-base font-semibold text-white mb-3">Replicated Orders</h2>
          <div className="space-y-2 max-h-[460px] overflow-y-auto pr-1">
            {orders.length === 0 ? (
              <div className="text-xs text-slate-500 text-center py-8">
                No orders placed yet. Place an order in the Customer Portal to observe replication.
              </div>
            ) : (
              orders.map((o) => (
                <div
                  key={o.orderId}
                  onClick={() => setSelectedOrder(o)}
                  className={`p-3 rounded-lg border cursor-pointer transition text-xs ${
                    selectedOrder?.orderId === o.orderId
                      ? 'bg-indigo-950/40 border-indigo-500 text-white'
                      : 'bg-slate-900 border-slate-800 hover:border-slate-700 text-slate-300'
                  }`}
                >
                  <div className="flex items-center justify-between font-bold">
                    <span>Order #{o.orderId}</span>
                    <span className="px-2 py-0.5 rounded text-[10px] bg-slate-800 font-mono text-indigo-300">
                      {o.status}
                    </span>
                  </div>
                  <div className="text-slate-400 mt-1 truncate">
                    {o.customerName} • {o.itemName} (x{o.quantity})
                  </div>
                  <div className="flex items-center justify-between text-[10px] text-slate-500 mt-2 pt-1 border-t border-slate-800/60 font-mono">
                    <span>Events: {o.eventLog.length}</span>
                    <span>Bids: {o.acceptances.length}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Distributed Causal Timeline for Selected Order */}
        <div className="lg:col-span-2 bg-[#111827] border border-slate-800 rounded-xl p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between border-b border-slate-800 pb-3 mb-4">
              <div>
                <h2 className="text-base font-semibold text-white">
                  {selectedOrder ? `Distributed Event Timeline: Order #${selectedOrder.orderId}` : 'Select an Order'}
                </h2>
                <p className="text-xs text-slate-400">
                  Total causal event history ordered strictly by Lamport logical timestamps
                </p>
              </div>
              {selectedOrder && (
                <span className="px-2.5 py-1 rounded bg-indigo-600/20 text-indigo-300 text-xs font-mono border border-indigo-500/30">
                  Status: {selectedOrder.status}
                </span>
              )}
            </div>

            {selectedOrder ? (
              <div className="space-y-4">
                {/* Event timeline sequence */}
                <div className="space-y-3">
                  {selectedOrder.eventLog.map((ev, idx) => (
                    <div
                      key={ev.eventId || idx}
                      className="p-3 bg-slate-900/90 border border-slate-800 rounded-lg flex items-start justify-between gap-4 text-xs"
                    >
                      <div className="flex items-start gap-3">
                        <div className="p-2 bg-indigo-500/20 text-indigo-400 rounded-lg font-mono text-xs">
                          #{idx + 1}
                        </div>
                        <div>
                          <div className="font-bold text-white flex items-center gap-2">
                            <span>{ev.eventType}</span>
                            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-indigo-300">
                              {ev.eventId}
                            </span>
                          </div>
                          <p className="text-slate-400 mt-0.5">
                            Source: <strong className="text-slate-300">{ev.sourceId}</strong>
                          </p>
                          {ev.physicalTimestamp && (
                            <p className="text-[11px] text-slate-500 font-mono mt-0.5">
                              Physical Action Time: {new Date(ev.physicalTimestamp).toLocaleTimeString()} ({ev.physicalTimestamp} ms)
                            </p>
                          )}
                        </div>
                      </div>

                      <div className="text-right">
                        <div className="text-indigo-400 font-mono font-bold">
                          Lamport: {ev.lamportTimestamp}
                        </div>
                        <span className="text-[10px] text-emerald-400 font-medium">Replicated</span>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Rider Acceptance Bids Evaluation Section */}
                {selectedOrder.acceptances.length > 0 && (
                  <div className="mt-4 p-4 bg-slate-950 rounded-xl border border-slate-800">
                    <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-2">
                      Rider Acceptance Bids (Physical Timestamp Tie-Breaking)
                    </h3>
                    <div className="space-y-2">
                      {selectedOrder.acceptances.map((acc, i) => (
                        <div
                          key={acc.riderId}
                          className="flex items-center justify-between text-xs p-2 rounded bg-slate-900 border border-slate-800"
                        >
                          <span className="font-semibold text-white">Rider {acc.riderId}</span>
                          <span className="font-mono text-slate-400">
                            Physical: {new Date(acc.physicalTimestamp).toLocaleTimeString()} ({acc.physicalTimestamp} ms)
                          </span>
                          <span className="font-mono text-indigo-300">
                            L: {acc.lamportTimestamp}
                          </span>
                          {selectedOrder.assignedRider === acc.riderId ? (
                            <span className="px-2 py-0.5 rounded bg-amber-500/20 text-amber-400 border border-amber-500/30 text-[10px] font-bold">
                              WINNER (Earliest)
                            </span>
                          ) : (
                            <span className="text-slate-500 text-[10px]">Bidder</span>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="text-center py-16 text-slate-500 text-xs">
                Select an order on the left to inspect its RMI replication flow.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
