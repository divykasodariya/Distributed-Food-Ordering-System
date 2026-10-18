import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from './components/Layout';
import { DashboardPage } from './pages/DashboardPage';
import { TopologyPage } from './pages/TopologyPage';
import { RmiReplicationPage } from './pages/RmiReplicationPage';
import { LamportClocksPage } from './pages/LamportClocksPage';
import { CristianSyncPage } from './pages/CristianSyncPage';
import { BullyElectionPage } from './pages/BullyElectionPage';
import { HeartbeatPage } from './pages/HeartbeatPage';
import { PersistenceRecoveryPage } from './pages/PersistenceRecoveryPage';
import { LoadBalancingPage } from './pages/LoadBalancingPage';
import { ConcurrencyPage } from './pages/ConcurrencyPage';
import { CustomerAppPage } from './pages/CustomerAppPage';
import { RiderAppPage } from './pages/RiderAppPage';
import { EventExplorerPage } from './pages/EventExplorerPage';
import { DemoScenariosPage } from './pages/DemoScenariosPage';

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="topology" element={<TopologyPage />} />
          <Route path="rmi-replication" element={<RmiReplicationPage />} />
          <Route path="lamport-clocks" element={<LamportClocksPage />} />
          <Route path="cristian-sync" element={<CristianSyncPage />} />
          <Route path="bully-election" element={<BullyElectionPage />} />
          <Route path="heartbeat" element={<HeartbeatPage />} />
          <Route path="persistence-recovery" element={<PersistenceRecoveryPage />} />
          <Route path="load-balancing" element={<LoadBalancingPage />} />
          <Route path="concurrency" element={<ConcurrencyPage />} />
          <Route path="customer-app" element={<CustomerAppPage />} />
          <Route path="rider-app" element={<RiderAppPage />} />
          <Route path="event-explorer" element={<EventExplorerPage />} />
          <Route path="demo-scenarios" element={<DemoScenariosPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
