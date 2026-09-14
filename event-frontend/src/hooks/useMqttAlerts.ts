/**
 * Real-time MQTT subscriber hook for low-latency emergency alert delivery (Fast-Path).
 * Bypasses backend REST polling by connecting directly to edge broker WebSockets (port 9001),
 * listening for instant critical alarms and triggering native browser notifications.
 */

import { useEffect, useRef } from 'react';
import mqtt, { type MqttClient } from 'mqtt';
import type { NodeDTO } from '../types';

/**
 * Triggers a native desktop notification via the Web Notification API when granted,
 * or falls back to a standard browser alert dialog to notify users of critical crowd thresholds.
 *
 * @param areaId The identifier of the monitored area triggering the alert.
 * @param cause The underlying reason or trend description of the alert.
 * @param timestamp The ISO timestamp when the alert occurred.
 */
function spawnBrowserAlert(areaId: string, cause: string, timestamp: string): void {
  const timeFormatted = new Date(timestamp).toLocaleTimeString();
  const title = `CRITICAL ALERT: AREA ${areaId.toUpperCase()}`;
  const body = `Cause: ${cause}\nTime: ${timeFormatted}`;

  if ('Notification' in window && Notification.permission === 'granted') {
    const notification = new Notification(title, {
      body: body,
      tag: `alert-${areaId}-${timestamp}`,
      requireInteraction: true,
    });

    notification.onclick = () => {
      window.focus();
      notification.close();
    };
  } else {
    window.alert(`${title}\n\n${body}`);
  }
}

/**
 * Establishes and manages WebSocket MQTT client subscriptions to each active cluster node's broker.
 * Listens for messages on topic 'event/alerts/#' and triggers native browser alerts for instant response,
 * ensuring all active client connections are gracefully terminated upon component unmount or node changes.
 *
 * @param nodes List of cluster nodes providing broker endpoints for WebSocket subscriptions.
 */
export const useMqttAlerts = (nodes: NodeDTO[]): void => {
  const clientsRef = useRef<MqttClient[]>([]);

  useEffect(() => {
    if (!nodes || nodes.length === 0) return;

    clientsRef.current.forEach((client) => client.end(true));
    clientsRef.current = [];

    nodes.forEach((node) => {
      const ipMatch = node.brokerUrl?.match(/tcp:\/\/([^:]+):/);
      const hostIp = ipMatch ? ipMatch[1] : node.name;
      const wsUrl = `ws://${hostIp}:9001`;

      try {
        const client = mqtt.connect(wsUrl, {
          clientId: `notifier-${node.id || node.name}-${Math.random().toString(16).substring(2, 8)}`,
          keepalive: 30,
          reconnectPeriod: 5000,
        });

        client.on('connect', () => {
          client.subscribe('event/alerts/#', { qos: 1 });
        });

        client.on('message', (topic, payload) => {
          try {
            const rawAlert = JSON.parse(payload.toString());
            const areaId = topic.split('/').pop() || rawAlert.area_id || rawAlert.areaId || 'UNKNOWN';
            const cause = rawAlert.cause || 'Critical crowd trend detected';
            const ts = rawAlert.ts || new Date().toISOString();

            spawnBrowserAlert(areaId, cause, ts);
          } catch (e) {
            console.error('Error parsing MQTT message payload:', e);
          }
        });

        client.on('error', (err) => {
          console.warn(`[Fast-Path Listener] Connection failure for ${node.name}:`, err.message);
        });

        clientsRef.current.push(client);
      } catch (err) {
        console.error(`Unable to start MQTT listener for node ${node.name}:`, err);
      }
    });

    return () => {
      clientsRef.current.forEach((client) => client.end(true));
      clientsRef.current = [];
    };
  }, [nodes]);
};