export interface NodeStatus {
  nodeId: string;
  host: string;
  port: number;
  role: 'PRIMARY' | 'BACKUP';
  isPrimary: boolean;
  healthStatus: 'ALIVE' | 'UNREACHABLE' | 'DEAD';
  reachable: boolean;
  latestLogVersion: number;
  lastPingTime: number;
}

export interface ClusterInfo {
  gateway: {
    status: string;
    port: number;
    url: string;
  };
  primaryNodeId: string;
  nodes: {
    nodeId: string;
    host: string;
    port: number;
    role: 'PRIMARY' | 'BACKUP';
    healthStatus: string;
    rmiUrl: string;
  }[];
  timestamp: number;
}

export interface RiderAcceptance {
  riderId: string;
  physicalTimestamp: number;
  serverReceiptTimestamp: number;
  lamportTimestamp: number;
}

export interface OrderEvent {
  eventId: string;
  orderId: number;
  eventType: string;
  sourceId: string;
  lamportTimestamp: number;
  physicalTimestamp?: number | null;
}

export interface OrderSnapshot {
  orderId: number;
  customerName: string;
  restaurantName: string;
  itemName: string;
  quantity: number;
  status: 'WAITING_FOR_RIDER' | 'ASSIGNED' | 'RIDER_ARRIVED' | 'DELIVERED' | 'CANCELLED';
  assignedRider: string | null;
  creationTime: number;
  assignmentTime: number;
  deadline: number;
  acceptances: RiderAcceptance[];
  eventLog: OrderEvent[];
}

export interface SystemTelemetryEvent {
  eventId: string;
  category: string;
  sourceNode: string;
  targetNode: string;
  lamportTime: number;
  physicalTime: number;
  orderId: number | null;
  details: string;
  success: boolean;
}

export interface MetricsData {
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  averageLatencyMs: number;
  maxLatencyMs: number;
  timestamp: number;
}

export interface MenuItem {
  name: string;
  price: number;
}

export interface Restaurant {
  id: string;
  name: string;
  items: MenuItem[];
}

export interface CristianSyncResult {
  t0: number;
  serverTime: number;
  t1: number;
  rtt: number;
  oneWayDelay: number;
  offset: number;
  synchronizedTime: number;
}

export interface NodeRecoveryLog {
  nodeId: string;
  filePath: string;
  exists: boolean;
  totalEvents: number;
  latestVersion: number;
  recentEntries: string[];
}
