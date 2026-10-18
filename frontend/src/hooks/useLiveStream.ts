import { useState, useEffect } from 'react';
import type { SystemTelemetryEvent } from '../types';

export function useLiveStream() {
  const [events, setEvents] = useState<SystemTelemetryEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState<SystemTelemetryEvent | null>(null);

  useEffect(() => {
    // Initial fetch of recent events
    fetch('/api/events?limit=50')
      .then(res => res.json())
      .then(data => {
        if (Array.isArray(data)) {
          setEvents(data);
          if (data.length > 0) {
            setLastEvent(data[data.length - 1]);
          }
        }
      })
      .catch(console.error);

    // Setup Server-Sent Events (SSE)
    const eventSource = new EventSource('/api/stream');

    eventSource.onopen = () => {
      setConnected(true);
    };

    eventSource.onmessage = (e) => {
      try {
        const parsed = JSON.parse(e.data);
        if (parsed.eventId) {
          setEvents(prev => [parsed, ...prev.slice(0, 199)]);
          setLastEvent(parsed);
        }
      } catch (err) {
        console.error('Failed to parse SSE event:', err);
      }
    };

    eventSource.onerror = () => {
      setConnected(false);
    };

    return () => {
      eventSource.close();
    };
  }, []);

  return { events, connected, lastEvent };
}
