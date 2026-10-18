import React, { useState } from 'react';
import {
  ListFilter,
  Search,
  Download,
  Filter,
  CheckCircle,
  Clock,
  Layers,
  ArrowRight
} from 'lucide-react';
import type { SystemTelemetryEvent } from '../types';
import { useLiveStream } from '../hooks/useLiveStream';

export const EventExplorerPage: React.FC = () => {
  const { events } = useLiveStream();
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [selectedEvent, setSelectedEvent] = useState<SystemTelemetryEvent | null>(null);

  const filteredEvents = events.filter((e) => {
    const matchesSearch =
      searchTerm === '' ||
      e.eventId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      e.details.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (e.orderId && e.orderId.toString().includes(searchTerm));

    const matchesCategory =
      categoryFilter === 'ALL' || e.category === categoryFilter;

    return matchesSearch && matchesCategory;
  });

  const exportToJson = () => {
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(events, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', dataStr);
    downloadAnchor.setAttribute('download', `distributed_events_${Date.now()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white flex items-center gap-2">
            <ListFilter className="w-6 h-6 text-indigo-400" />
            Distributed Event Explorer & Log Auditing
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Global search, filtering, and deep inspection of all distributed lifecycle events and Lamport logical clock states
          </p>
        </div>

        <button
          onClick={exportToJson}
          className="flex items-center gap-1.5 px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium border border-slate-700 transition"
        >
          <Download className="w-3.5 h-3.5" />
          Export JSON Telemetry
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-[#111827] border border-slate-800 rounded-xl p-4 flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="relative w-full md:w-96">
          <Search className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search by eventId, orderId, or details..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2 rounded-lg bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500"
          />
        </div>

        <div className="flex items-center gap-2 w-full md:w-auto">
          <Filter className="w-4 h-4 text-slate-400" />
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="p-2 rounded-lg bg-slate-900 border border-slate-700 text-xs text-white font-mono"
          >
            <option value="ALL">All Event Categories</option>
            <option value="ORDER_CREATED">ORDER_CREATED</option>
            <option value="RIDER_ACCEPTED">RIDER_ACCEPTED</option>
            <option value="ASSIGNED">ASSIGNED</option>
            <option value="RIDER_ARRIVED">RIDER_ARRIVED</option>
            <option value="DELIVERED">DELIVERED</option>
            <option value="CLOCK_SYNCHRONIZED">CLOCK_SYNCHRONIZED</option>
            <option value="ELECTION_STARTED">ELECTION_STARTED</option>
            <option value="NODE_UNAVAILABLE">NODE_UNAVAILABLE</option>
          </select>
        </div>
      </div>

      {/* Events Table & Details Drawer */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-[#111827] border border-slate-800 rounded-xl overflow-hidden">
          <div className="p-4 border-b border-slate-800 flex justify-between items-center text-xs text-slate-400">
            <span>Showing {filteredEvents.length} events</span>
            <span className="font-mono text-[10px]">Real-Time SSE Stream</span>
          </div>

          <div className="overflow-x-auto max-h-[500px]">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-900/80 text-slate-400 font-mono text-[11px] border-b border-slate-800">
                <tr>
                  <th className="py-3 px-4">Event ID</th>
                  <th className="py-3 px-4">Category</th>
                  <th className="py-3 px-4">Nodes</th>
                  <th className="py-3 px-4">Lamport</th>
                  <th className="py-3 px-4">Timestamp</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 font-mono">
                {filteredEvents.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-12 text-center text-slate-500">
                      No matching distributed events found.
                    </td>
                  </tr>
                ) : (
                  filteredEvents.map((e) => (
                    <tr
                      key={e.eventId}
                      onClick={() => setSelectedEvent(e)}
                      className={`cursor-pointer transition hover:bg-slate-800/40 ${
                        selectedEvent?.eventId === e.eventId ? 'bg-indigo-950/40 text-white' : 'text-slate-300'
                      }`}
                    >
                      <td className="py-3 px-4 text-indigo-400 font-semibold">{e.eventId}</td>
                      <td className="py-3 px-4">
                        <span className="px-2 py-0.5 rounded text-[10px] bg-slate-800 font-bold text-slate-200">
                          {e.category}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-[11px] text-slate-400">
                        {e.sourceNode} → {e.targetNode || 'ALL'}
                      </td>
                      <td className="py-3 px-4 text-emerald-400 font-bold">L:{e.lamportTime}</td>
                      <td className="py-3 px-4 text-slate-500 text-[11px]">
                        {new Date(e.physicalTime).toLocaleTimeString()}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Selected Event Details Card */}
        <div className="bg-[#111827] border border-slate-800 rounded-xl p-5 flex flex-col justify-between">
          <div>
            <h2 className="text-base font-semibold text-white border-b border-slate-800 pb-3 mb-4">
              Event Inspector
            </h2>

            {selectedEvent ? (
              <div className="space-y-3 text-xs">
                <div>
                  <span className="text-slate-500 block text-[10px] font-mono">EVENT ID</span>
                  <span className="font-bold font-mono text-indigo-300 text-sm">{selectedEvent.eventId}</span>
                </div>

                <div>
                  <span className="text-slate-500 block text-[10px] font-mono">CATEGORY</span>
                  <span className="font-bold text-white">{selectedEvent.category}</span>
                </div>

                <div>
                  <span className="text-slate-500 block text-[10px] font-mono">ASSOCIATED ORDER</span>
                  <span className="font-mono text-amber-400 font-bold">
                    {selectedEvent.orderId ? `Order #${selectedEvent.orderId}` : 'None'}
                  </span>
                </div>

                <div>
                  <span className="text-slate-500 block text-[10px] font-mono">COMMUNICATION VECTOR</span>
                  <span className="font-mono text-slate-300">
                    {selectedEvent.sourceNode} ➔ {selectedEvent.targetNode || 'CLUSTER'}
                  </span>
                </div>

                <div>
                  <span className="text-slate-500 block text-[10px] font-mono">LAMPORT LOGICAL TIME</span>
                  <span className="font-mono font-bold text-emerald-400">{selectedEvent.lamportTime}</span>
                </div>

                <div>
                  <span className="text-slate-500 block text-[10px] font-mono">PHYSICAL TIMESTAMP</span>
                  <span className="font-mono text-slate-300">
                    {new Date(selectedEvent.physicalTime).toISOString()} ({selectedEvent.physicalTime} ms)
                  </span>
                </div>

                <div className="p-3 bg-slate-900 rounded-lg border border-slate-800 mt-2">
                  <span className="text-slate-500 block text-[10px] font-mono mb-1">EVENT DETAILS</span>
                  <p className="text-slate-200">{selectedEvent.details}</p>
                </div>
              </div>
            ) : (
              <div className="text-center py-20 text-slate-500 text-xs">
                Select an event from the table to view its full distributed metadata.
              </div>
            )}
          </div>

          <div className="p-3 bg-slate-950 rounded-lg border border-slate-800 text-[11px] text-slate-400 mt-4">
            Total causal order is guaranteed via <code className="text-indigo-300 font-mono">(lamport, sourceId, eventId)</code>.
          </div>
        </div>
      </div>
    </div>
  );
};
