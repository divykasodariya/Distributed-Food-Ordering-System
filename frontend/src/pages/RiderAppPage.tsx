import React, { useEffect, useState } from 'react';
import {
  Bike,
  CheckCircle,
  Clock,
  ArrowRight,
  Send,
  Timer,
  ShieldCheck,
  MapPin,
  PackageCheck
} from 'lucide-react';
import type { OrderSnapshot } from '../types';
import { useLiveStream } from '../hooks/useLiveStream';

export const RiderAppPage: React.FC = () => {
  const [riderId, setRiderId] = useState<string>('R1');
  const [orders, setOrders] = useState<OrderSnapshot[]>([]);
  const [actionStatus, setActionStatus] = useState<string | null>(null);
  const [loadingOrderId, setLoadingOrderId] = useState<number | null>(null);
  const { events } = useLiveStream();

  const fetchOrders = async () => {
    try {
      const res = await fetch('/api/orders');
      const data = await res.json();
      setOrders(data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchOrders();
    const timer = setInterval(fetchOrders, 3000);
    return () => clearInterval(timer);
  }, [events]);

  const handleAction = async (action: 'accept' | 'finalize' | 'arrive' | 'deliver', orderId: number) => {
    setLoadingOrderId(orderId);
    setActionStatus(null);

    try {
      const res = await fetch('/api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action,
          orderId: orderId.toString(),
          riderId
        })
      });

      const data = await res.json();
      if (data.success) {
        setActionStatus(`Action '${action}' succeeded for Order #${orderId}`);
        fetchOrders();
      } else {
        setActionStatus(`Action '${action}' failed or already finalized.`);
      }
    } catch (err) {
      setActionStatus('Action error: ' + err);
    } finally {
      setLoadingOrderId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white flex items-center gap-2">
            <Bike className="w-6 h-6 text-purple-400" />
            Rider Delivery Management Portal
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Accept orders with synchronized physical timestamps, evaluate earliest-bid assignment, and execute delivery lifecycle
          </p>
        </div>

        {/* Rider Identity Toggle */}
        <div className="flex items-center gap-2 text-xs">
          <span className="text-slate-400">Active Rider ID:</span>
          {['R1', 'R2', 'R3'].map((id) => (
            <button
              key={id}
              onClick={() => setRiderId(id)}
              className={`px-3 py-1.5 rounded-lg font-bold font-mono transition ${
                riderId === id
                  ? 'bg-purple-600 text-white shadow'
                  : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              Rider {id}
            </button>
          ))}
        </div>
      </div>

      {actionStatus && (
        <div className="p-3 bg-purple-950/40 border border-purple-800/40 rounded-lg text-purple-200 text-xs flex items-center justify-between">
          <span>{actionStatus}</span>
          <CheckCircle className="w-4 h-4 text-purple-400" />
        </div>
      )}

      {/* Orders Available for Rider Interaction */}
      <div className="bg-[#111827] border border-slate-800 rounded-xl p-5">
        <h2 className="text-base font-semibold text-white mb-4">Available Delivery Tasks</h2>

        <div className="space-y-3">
          {orders.length === 0 ? (
            <div className="text-center py-12 text-slate-500 text-xs">
              No orders found in cluster. Place an order in the Customer App first.
            </div>
          ) : (
            orders.map((o) => {
              const isWaiting = o.status === 'WAITING_FOR_RIDER';
              const isAssigned = o.status === 'ASSIGNED';
              const isArrived = o.status === 'RIDER_ARRIVED';
              const isDelivered = o.status === 'DELIVERED';
              const isMyAssignment = o.assignedRider === riderId;

              return (
                <div
                  key={o.orderId}
                  className="p-4 bg-slate-900 rounded-xl border border-slate-800 flex flex-col md:flex-row items-start md:items-center justify-between gap-4 text-xs"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-white text-sm">Order #{o.orderId}</span>
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-800 text-indigo-300 font-mono">
                        {o.status}
                      </span>
                      {o.assignedRider && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-500/20 text-purple-300 border border-purple-500/30">
                          Assigned to: {o.assignedRider}
                        </span>
                      )}
                    </div>
                    <div className="text-slate-400 mt-1">
                      {o.restaurantName} • {o.itemName} (x{o.quantity}) for {o.customerName}
                    </div>
                    <div className="text-[10px] text-slate-500 font-mono mt-1">
                      Bids: {o.acceptances.length} riders recorded
                    </div>
                  </div>

                  {/* Contextual Rider Actions */}
                  <div className="flex items-center gap-2 flex-wrap">
                    {isWaiting && (
                      <>
                        <button
                          onClick={() => handleAction('accept', o.orderId)}
                          disabled={loadingOrderId === o.orderId}
                          className="px-3 py-1.5 bg-purple-600 hover:bg-purple-500 text-white rounded-lg font-medium transition shadow"
                        >
                          Accept Delivery Bid
                        </button>
                        <button
                          onClick={() => handleAction('finalize', o.orderId)}
                          disabled={loadingOrderId === o.orderId || o.acceptances.length === 0}
                          className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:bg-slate-800 disabled:text-slate-600 text-white rounded-lg font-medium transition"
                        >
                          Finalize Assignment
                        </button>
                      </>
                    )}

                    {isAssigned && isMyAssignment && (
                      <button
                        onClick={() => handleAction('arrive', o.orderId)}
                        disabled={loadingOrderId === o.orderId}
                        className="px-3 py-1.5 bg-amber-600 hover:bg-amber-500 text-slate-950 font-bold rounded-lg transition flex items-center gap-1.5"
                      >
                        <MapPin className="w-3.5 h-3.5" />
                        Arrive at Restaurant
                      </button>
                    )}

                    {isArrived && isMyAssignment && (
                      <button
                        onClick={() => handleAction('deliver', o.orderId)}
                        disabled={loadingOrderId === o.orderId}
                        className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-medium rounded-lg transition flex items-center gap-1.5"
                      >
                        <PackageCheck className="w-3.5 h-3.5" />
                        Mark as Delivered
                      </button>
                    )}

                    {isDelivered && (
                      <span className="text-emerald-400 font-bold flex items-center gap-1">
                        <CheckCircle className="w-4 h-4" />
                        Delivered
                      </span>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
