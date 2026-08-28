// src/hooks/useMqttAlerts.ts
import { useEffect, useRef } from 'react';
import mqtt, { type MqttClient } from 'mqtt';
import type { NodeDTO } from '../types';

export const useMqttAlerts = (nodes: NodeDTO[]) => {
  const clientsRef = useRef<MqttClient[]>([]);

  // Funzione per generare il popup nativo del browser (Web Notification API)
  const spawnBrowserAlert = (areaId: string, cause: string, timestamp: string) => {
    const timeFormatted = new Date(timestamp).toLocaleTimeString();
    const title = `ALLARME CRITICO: AREA ${areaId.toUpperCase()}`;
    const body = `Causa: ${cause}\nOra: ${timeFormatted}`;

    // 1. Notifica Desktop di sistema
    if ('Notification' in window && Notification.permission === 'granted') {
      const notification = new Notification(title, {
        body: body,
        tag: `alert-${areaId}-${timestamp}`, // Evita popup identici duplicati
        requireInteraction: true,            // Rimane visibile finché non viene cliccata
      });

      notification.onclick = () => {
        window.focus();
        notification.close();
      };
    } else {
      // 2. Fallback con window.alert standard
      window.alert(`${title}\n\n${body}`);
    }
  };

  useEffect(() => {
    if (!nodes || nodes.length === 0) return;

    // Chiude eventuali socket aperti in precedenza
    clientsRef.current.forEach((client) => client.end(true));
    clientsRef.current = [];

    // Crea un client WebSocket per ciascun broker dei nodi
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
          console.log(`[Fast-Path Listener] Connesso a ${node.name} (${wsUrl})`);
          client.subscribe('event/alerts/#', { qos: 1 });
        });

        client.on('message', (topic, payload) => {
          try {
            const rawAlert = JSON.parse(payload.toString());
            
            // Estrae area dal topic (es. 'event/alerts/stage' -> 'stage') o dal payload
            const areaId = topic.split('/').pop() || rawAlert.area_id || rawAlert.areaId || 'SCONOSCIUTA';
            const cause = rawAlert.cause || 'Trend di affollamento critico';
            const ts = rawAlert.ts || new Date().toISOString();

            console.log(`⚡ [FAST-PATH RICEVUTO DA ${node.name}] Area: ${areaId}`);

            // FA SPAWNARE ESCLUSIVAMENTE LA NOTIFICA BROWSER
            spawnBrowserAlert(areaId, cause, ts);
          } catch (e) {
            console.error('Errore durante il parsing del messaggio MQTT:', e);
          }
        });

        client.on('error', (err) => {
          console.warn(`[Fast-Path Listener] Connessione non riuscita per ${node.name}:`, err.message);
        });

        clientsRef.current.push(client);
      } catch (err) {
        console.error(`Impossibile avviare il listener per il nodo ${node.name}:`, err);
      }
    });

    // Cleanup: chiusura socket allo smontaggio
    return () => {
      clientsRef.current.forEach((client) => client.end(true));
      clientsRef.current = [];
    };
  }, [nodes]);
};