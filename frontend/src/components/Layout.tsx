import React, { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
  LayoutDashboard,
  Network,
  Share2,
  Clock,
  Timer,
  Crown,
  Activity,
  HardDrive,
  Scale,
  Cpu,
  ShoppingBag,
  Bike,
  ListFilter,
  PlayCircle,
  Menu,
  X,
  Server,
  Radio,
  Wifi,
  WifiOff
} from 'lucide-react';
import { useLiveStream } from '../hooks/useLiveStream';

const navItems = [
  { path: '/', label: 'Overview Dashboard', icon: LayoutDashboard },
  { path: '/topology', label: 'Cluster Topology', icon: Network },
  { path: '/rmi-replication', label: 'RMI & Replication', icon: Share2 },
  { path: '/lamport-clocks', label: 'Lamport Clocks', icon: Clock },
  { path: '/cristian-sync', label: 'Cristian Time Sync', icon: Timer },
  { path: '/bully-election', label: 'Bully Election & Failover', icon: Crown },
  { path: '/heartbeat', label: 'Heartbeat Monitor', icon: Activity },
  { path: '/persistence-recovery', label: 'Persistence & Recovery', icon: HardDrive },
  { path: '/load-balancing', label: 'Weighted Round-Robin', icon: Scale },
  { path: '/concurrency', label: 'Concurrency & Performance', icon: Cpu },
  { path: '/customer-app', label: 'Customer Order App', icon: ShoppingBag },
  { path: '/rider-app', label: 'Rider Portal', icon: Bike },
  { path: '/event-explorer', label: 'Distributed Event Explorer', icon: ListFilter },
  { path: '/demo-scenarios', label: 'Guided Demonstrations', icon: PlayCircle },
];

export const Layout: React.FC = () => {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const { connected, lastEvent } = useLiveStream();

  return (
    <div className="flex h-screen bg-[#0b0f19] text-slate-100 overflow-hidden font-sans">
      {/* Sidebar */}
      <aside
        className={`${
          sidebarOpen ? 'w-64' : 'w-20'
        } transition-all duration-300 ease-in-out bg-[#0f172a] border-r border-slate-800 flex flex-col z-20`}
      >
        <div className="h-16 flex items-center justify-between px-4 border-b border-slate-800">
          <div className="flex items-center space-x-3 overflow-hidden">
            <div className="p-2 bg-indigo-600/20 text-indigo-400 rounded-lg border border-indigo-500/30">
              <Server className="w-5 h-5" />
            </div>
            {sidebarOpen && (
              <div className="truncate">
                <span className="font-bold text-sm tracking-wide text-white block">DISTRIBUTED</span>
                <span className="text-[10px] text-indigo-400 font-mono tracking-wider block">FOOD ORDERING</span>
              </div>
            )}
          </div>
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition"
          >
            {sidebarOpen ? <X className="w-4 h-4" /> : <Menu className="w-4 h-4" />}
          </button>
        </div>

        {/* Navigation list */}
        <nav className="flex-1 overflow-y-auto py-4 px-2 space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `flex items-center space-x-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-indigo-600/20 text-indigo-300 border border-indigo-500/40 shadow-sm'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                  }`
                }
              >
                <Icon className="w-5 h-5 flex-shrink-0" />
                {sidebarOpen && <span className="truncate">{item.label}</span>}
              </NavLink>
            );
          })}
        </nav>

        {/* Live SSE Status Footer */}
        <div className="p-3 border-t border-slate-800 bg-slate-900/50">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <div className={`w-2.5 h-2.5 rounded-full ${connected ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'}`} />
              {sidebarOpen && (
                <span className="text-xs text-slate-400">
                  {connected ? 'Live RMI Stream' : 'Disconnected'}
                </span>
              )}
            </div>
            {sidebarOpen && (
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-400">
                SSE :8080
              </span>
            )}
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col h-full overflow-hidden">
        {/* Top Header */}
        <header className="h-16 bg-[#0f172a]/80 backdrop-blur border-b border-slate-800 flex items-center justify-between px-6 z-10">
          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-2 px-2.5 py-1 rounded-md bg-slate-800/70 border border-slate-700/60 text-xs">
              <Radio className="w-3.5 h-3.5 text-indigo-400 animate-pulse" />
              <span className="text-slate-300 font-mono">Cluster: Node1 (1101), Node2 (1102), Node3 (1103)</span>
            </div>
            <div className="flex items-center space-x-2 px-2.5 py-1 rounded-md bg-slate-800/70 border border-slate-700/60 text-xs">
              <span className="text-slate-400">Gateway:</span>
              <span className="text-emerald-400 font-mono">:1099</span>
            </div>
          </div>

          {/* Last Broadcasted Event Marquee */}
          <div className="flex items-center space-x-3">
            {lastEvent ? (
              <div className="flex items-center space-x-2 text-xs bg-slate-800/90 border border-slate-700 px-3 py-1.5 rounded-full shadow-sm max-w-md truncate">
                <span className="w-2 h-2 rounded-full bg-indigo-400"></span>
                <span className="text-indigo-300 font-semibold uppercase tracking-wider">{lastEvent.category}</span>
                <span className="text-slate-400 truncate">{lastEvent.details}</span>
              </div>
            ) : (
              <span className="text-xs text-slate-500 italic">Listening for cluster telemetry...</span>
            )}

            <div className="flex items-center space-x-1.5 pl-3 border-l border-slate-800">
              {connected ? (
                <Wifi className="w-4 h-4 text-emerald-400" />
              ) : (
                <WifiOff className="w-4 h-4 text-rose-400" />
              )}
            </div>
          </div>
        </header>

        {/* Page Container */}
        <main className="flex-1 overflow-y-auto p-6 bg-[#0b0f19]">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
