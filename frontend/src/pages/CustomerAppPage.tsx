import React, { useEffect, useState } from 'react';
import {
  ShoppingBag,
  PlusCircle,
  Clock,
  CheckCircle,
  Search,
  Sparkles,
  Server
} from 'lucide-react';
import type { Restaurant, OrderSnapshot } from '../types';
import { useLiveStream } from '../hooks/useLiveStream';

export const CustomerAppPage: React.FC = () => {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState<string>('Restaurant A');
  const [customerName, setCustomerName] = useState<string>('Alice Johnson');
  const [selectedItem, setSelectedItem] = useState<string>('Margherita Pizza');
  const [quantity, setQuantity] = useState<number>(1);
  const [orders, setOrders] = useState<OrderSnapshot[]>([]);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [placedOrder, setPlacedOrder] = useState<{ id: number; lamport: number } | null>(null);
  const { events } = useLiveStream();

  const fetchCatalogAndOrders = async () => {
    try {
      const [menuRes, ordersRes] = await Promise.all([
        fetch('/api/menu').then(r => r.json()),
        fetch('/api/orders').then(r => r.json())
      ]);
      setRestaurants(menuRes);
      setOrders(ordersRes);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchCatalogAndOrders();
    const timer = setInterval(fetchCatalogAndOrders, 3000);
    return () => clearInterval(timer);
  }, [events]);

  const placeOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setPlacedOrder(null);

    try {
      const res = await fetch('/api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: 'create',
          customerName,
          restaurantName: selectedRestaurant,
          itemName: selectedItem,
          quantity
        })
      });

      const data = await res.json();
      if (data.success) {
        setPlacedOrder({ id: data.orderId, lamport: data.lamport });
        fetchCatalogAndOrders();
      }
    } catch (err) {
      console.error('Order creation failed:', err);
    } finally {
      setSubmitting(false);
    }
  };

  const activeRest = restaurants.find(r => r.name.includes(selectedRestaurant) || r.id === selectedRestaurant) || restaurants[0];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white flex items-center gap-2">
          <ShoppingBag className="w-6 h-6 text-indigo-400" />
          Customer Food Ordering Portal
        </h1>
        <p className="text-sm text-slate-400 mt-1">
          Place orders directly over Java RMI into the distributed state machine with Lamport clock attachment
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Create Order Form */}
        <div className="bg-[#111827] border border-slate-800 rounded-xl p-5">
          <h2 className="text-base font-semibold text-white mb-4 flex items-center gap-2">
            <PlusCircle className="w-5 h-5 text-indigo-400" />
            Place New Distributed Order
          </h2>

          <form onSubmit={placeOrder} className="space-y-4 text-xs">
            <div>
              <label className="text-slate-300 font-medium block mb-1">Customer Name:</label>
              <input
                type="text"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                required
                className="w-full p-2.5 rounded-lg bg-slate-900 border border-slate-700 text-white"
              />
            </div>

            <div>
              <label className="text-slate-300 font-medium block mb-1">Select Restaurant:</label>
              <select
                value={selectedRestaurant}
                onChange={(e) => {
                  setSelectedRestaurant(e.target.value);
                  const r = restaurants.find(res => res.name.includes(e.target.value));
                  if (r && r.items.length > 0) {
                    setSelectedItem(r.items[0].name);
                  }
                }}
                className="w-full p-2.5 rounded-lg bg-slate-900 border border-slate-700 text-white"
              >
                <option value="Restaurant A">Restaurant A (Pizza Palace)</option>
                <option value="Restaurant B">Restaurant B (Burger Bar)</option>
              </select>
            </div>

            <div>
              <label className="text-slate-300 font-medium block mb-1">Menu Item:</label>
              <select
                value={selectedItem}
                onChange={(e) => setSelectedItem(e.target.value)}
                className="w-full p-2.5 rounded-lg bg-slate-900 border border-slate-700 text-white"
              >
                {activeRest?.items.map((item) => (
                  <option key={item.name} value={item.name}>
                    {item.name} — ${item.price.toFixed(2)}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-slate-300 font-medium block mb-1">Quantity:</label>
              <input
                type="number"
                min="1"
                max="10"
                value={quantity}
                onChange={(e) => setQuantity(parseInt(e.target.value))}
                className="w-full p-2.5 rounded-lg bg-slate-900 border border-slate-700 text-white"
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="w-full py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 disabled:bg-indigo-900 text-white font-medium rounded-lg transition shadow"
            >
              {submitting ? 'Routing over RMI...' : 'Submit Order to Gateway'}
            </button>
          </form>

          {placedOrder && (
            <div className="mt-4 p-3 bg-emerald-950/40 border border-emerald-800/40 rounded-lg text-emerald-300 text-xs flex items-center justify-between">
              <div>
                <span className="font-bold block">Order Placed Successfully!</span>
                <span className="font-mono text-[11px]">ID: #{placedOrder.id} • Lamport: {placedOrder.lamport}</span>
              </div>
              <CheckCircle className="w-5 h-5 text-emerald-400" />
            </div>
          )}
        </div>

        {/* Live Customer Order History */}
        <div className="lg:col-span-2 bg-[#111827] border border-slate-800 rounded-xl p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between border-b border-slate-800 pb-3 mb-4">
              <div>
                <h2 className="text-base font-semibold text-white">Active Customer Orders</h2>
                <p className="text-xs text-slate-400">Queried via Gateway Smooth Weighted Round-Robin load balancer</p>
              </div>
              <span className="text-xs font-mono text-indigo-400 bg-indigo-950/60 px-2 py-1 rounded border border-indigo-800">
                {orders.length} Orders
              </span>
            </div>

            <div className="space-y-3 max-h-[420px] overflow-y-auto pr-1">
              {orders.length === 0 ? (
                <div className="text-center py-16 text-slate-500 text-xs">
                  No orders found. Use the form on the left to submit an order.
                </div>
              ) : (
                orders.map((o) => (
                  <div
                    key={o.orderId}
                    className="p-3.5 bg-slate-900 rounded-xl border border-slate-800 flex items-center justify-between text-xs"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-white text-sm">Order #{o.orderId}</span>
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-800 text-indigo-300 font-mono">
                          {o.status}
                        </span>
                      </div>
                      <div className="text-slate-400 mt-1">
                        {o.customerName} ordered <strong className="text-slate-300">{o.itemName} (x{o.quantity})</strong> from {o.restaurantName}
                      </div>
                      <div className="text-[10px] text-slate-500 font-mono mt-1">
                        Assigned Rider: <strong className="text-amber-400">{o.assignedRider || 'Waiting for Bids'}</strong>
                      </div>
                    </div>

                    <div className="text-right">
                      <span className="text-[10px] font-mono text-slate-500 block">
                        {new Date(o.creationTime).toLocaleTimeString()}
                      </span>
                      <span className="mt-1 px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 text-[10px] font-bold inline-block">
                        Replicated
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
