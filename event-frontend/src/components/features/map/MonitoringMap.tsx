import React, { useEffect, useRef, useState } from 'react';
import { MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet-draw';
import type { AreaDTO, NodeDTO, State } from '../../../types';

interface MonitoringMapProps {
  areas: AreaDTO[];
  nodes: NodeDTO[];
  onAreaDraw: (coords: number[][][], layer: L.Layer) => void;
  onNodeDraw: (lat: number, lng: number, layer: L.Layer) => void;
  onAreaEdit: (id: string, coords: number[][], dto: AreaDTO) => void;
  onNodeEdit: (id: string, lat: number, lng: number, dto: NodeDTO) => void;
  onAreaDelete: (id: string) => void;
  manualAlertMode?: boolean;
  onMapClick?: (lat: number, lng: number) => void;
}

const getStateColor = (state?: State): { color: string; fillColor: string; fillOpacity: number } => {
  switch (state) {
    case 'LOW':
      return { color: 'green', fillColor: 'green', fillOpacity: 0.4 };
    case 'MEDIUM':
      return { color: 'yellow', fillColor: 'yellow', fillOpacity: 0.4 };
    case 'HIGH':
      return { color: 'orange', fillColor: 'orange', fillOpacity: 0.4 };
    case 'CRITICAL':
      return { color: 'red', fillColor: 'red', fillOpacity: 0.6 };
    case 'NONE':
    default:
      return { color: 'gray', fillColor: 'transparent', fillOpacity: 0 }; // Default transparent
  }
};

// Componente per abilitare zoom e pan SOLO tenendo premuto Ctrl/Cmd
const CtrlKeyInteractionHandler: React.FC<{ isCtrlPressed: boolean }> = ({ isCtrlPressed }) => {
  const map = useMap();

  useEffect(() => {
    if (isCtrlPressed) {
      map.scrollWheelZoom.enable();
      map.dragging.enable();
    } else {
      map.scrollWheelZoom.disable();
      map.dragging.disable();
    }
  }, [map, isCtrlPressed]);

  return null;
};

const MapContent: React.FC<MonitoringMapProps & { isCtrlPressed: boolean }> = ({
  areas,
  nodes,
  onAreaDraw,
  onNodeDraw,
  onAreaEdit,
  onNodeEdit,
  onAreaDelete,
  manualAlertMode,
  onMapClick,
  isCtrlPressed
}) => {
  const map = useMap();
  const drawnItemsRef = useRef(new L.FeatureGroup());
  const drawControlRef = useRef<L.Control.Draw | null>(null);

  // Forza il ricalcolo delle dimensioni quando il contenitore si adatta
  useEffect(() => {
    map.invalidateSize();
  }, [map]);

  useEffect(() => {
    const drawnItems = drawnItemsRef.current;
    if (!map.hasLayer(drawnItems)) {
      map.addLayer(drawnItems);
    }

    if (!drawControlRef.current) {
      drawControlRef.current = new L.Control.Draw({
        edit: {
          featureGroup: drawnItems
        },
        draw: {
          polygon: {},
          marker: {},
          polyline: false,
          circle: false,
          rectangle: false,
          circlemarker: false
        }
      });
      map.addControl(drawControlRef.current);
    }

    drawnItems.clearLayers();

    nodes.forEach((node: NodeDTO) => {
      const marker = L.marker([node.latitude, node.longitude], { interactive: !manualAlertMode });
      (marker as any).backendId = node.id;
      (marker as any).isNode = true;
      (marker as any).dto = node;
      if (!manualAlertMode) {
        marker.bindPopup(`<b>Nodo:</b> ${node.name}<br/><b>Tipo:</b> ${node.type}`);
      }
      drawnItems.addLayer(marker);
    });

    areas.forEach((area: AreaDTO) => {
      if (area.boundary && area.boundary.coordinates && area.boundary.coordinates[0]) {
        const latlngs = area.boundary.coordinates[0].map((coord: number[]) => [coord[1], coord[0]] as [number, number]);
        
        const style = getStateColor(area.state);
        const polygon = L.polygon(latlngs, {
          ...style,
          interactive: !manualAlertMode
        });
        
        (polygon as any).backendId = area.name;
        (polygon as any).isArea = true;
        (polygon as any).dto = area;
        if (!manualAlertMode) {
          polygon.bindPopup(`<b>Area:</b> ${area.name}<br/><b>Capacità:</b> ${area.capacity}<br/><b>Stato:</b> ${area.state || 'NONE'}`);
        }
        drawnItems.addLayer(polygon);
      }
    });

  }, [map, areas, nodes, manualAlertMode]);

  useEffect(() => {
    const handleCreated = (e: any) => {
      const type = e.layerType;
      const layer = e.layer;
      
      if (type === 'polygon') {
        const latlngs = layer.getLatLngs();
        let coords: number[][] = [];
        if (Array.isArray(latlngs[0])) {
          coords = (latlngs[0] as L.LatLng[]).map((latlng: L.LatLng) => [latlng.lng, latlng.lat]);
        } else {
          coords = (latlngs as L.LatLng[]).map((latlng: L.LatLng) => [latlng.lng, latlng.lat]);
        }
        coords.push(coords[0]);
        onAreaDraw([coords], layer);
      } else if (type === 'marker') {
        const latlng = layer.getLatLng();
        onNodeDraw(latlng.lat, latlng.lng, layer);
      }
    };

    const handleEdited = (e: any) => {
      const layers = e.layers;
      layers.eachLayer((layer: any) => {
        if (layer.isArea) {
          const latlngs = layer.getLatLngs();
          let coords: number[][] = [];
          if (Array.isArray(latlngs[0])) {
            coords = (latlngs[0] as L.LatLng[]).map((latlng: L.LatLng) => [latlng.lng, latlng.lat]);
          } else {
            coords = (latlngs as L.LatLng[]).map((latlng: L.LatLng) => [latlng.lng, latlng.lat]);
          }
          coords.push(coords[0]);
          onAreaEdit(layer.backendId, coords, layer.dto);
        } else if (layer.isNode) {
          const latlng = layer.getLatLng();
          onNodeEdit(layer.backendId, latlng.lat, latlng.lng, layer.dto);
        }
      });
    };

    const handleDeleted = (e: any) => {
      const layers = e.layers;
      layers.eachLayer((layer: any) => {
        if (layer.isArea) {
          onAreaDelete(layer.backendId);
        } else if (layer.isNode) {
          alert("I nodi possono solo essere modificati (spostati), non eliminati.");
          drawnItemsRef.current.addLayer(layer);
        }
      });
    };

    map.on(L.Draw.Event.CREATED, handleCreated);
    map.on(L.Draw.Event.EDITED, handleEdited);
    map.on(L.Draw.Event.DELETED, handleDeleted);

    return () => {
      map.off(L.Draw.Event.CREATED, handleCreated);
      map.off(L.Draw.Event.EDITED, handleEdited);
      map.off(L.Draw.Event.DELETED, handleDeleted);
    };
  }, [map, onAreaDraw, onNodeDraw, onAreaEdit, onNodeEdit, onAreaDelete]);

  useMapEvents({
    click(e) {
      if (manualAlertMode && onMapClick) {
        onMapClick(e.latlng.lat, e.latlng.lng);
      }
    }
  });

  return <CtrlKeyInteractionHandler isCtrlPressed={isCtrlPressed} />;
};

const MonitoringMap: React.FC<MonitoringMapProps> = (props) => {
  const [isCtrlPressed, setIsCtrlPressed] = useState<boolean>(false);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Control' || e.key === 'Meta' || e.ctrlKey || e.metaKey) {
        setIsCtrlPressed(true);
      }
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      if (!e.ctrlKey && !e.metaKey) {
        setIsCtrlPressed(false);
      }
    };

    const handleBlur = () => {
      setIsCtrlPressed(false);
    };

    // Agganciamo gli ascoltatori al browser
    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    window.addEventListener('blur', handleBlur);

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
      window.removeEventListener('blur', handleBlur);
    };
  }, []);

  return (
    <div 
      style={{ 
        cursor: props.manualAlertMode ? 'crosshair' : isCtrlPressed ? 'grab' : 'default', 
        height: '100%', 
        width: '100%', 
        minHeight: '700px',
        position: 'relative'
      }}
    >
      {/* Badge di suggerimento visivo per l'utente */}
      <div 
        className={`position-absolute bottom-0 start-0 m-2 px-2 py-1 rounded small ${isCtrlPressed ? 'bg-success text-white' : 'bg-dark text-white opacity-75'}`}
        style={{ zIndex: 1000, pointerEvents: 'none', fontSize: '0.75rem' }}
      >
        {isCtrlPressed ? 'Mappa sbloccata (Trascina / Zoom attivo)' : 'Tieni premuto CTRL per muovere la mappa o zoomare'}
      </div>

      <MapContainer
        center={[44.4937544, 11.3409058]}
        zoom={17}
        scrollWheelZoom={false}
        dragging={false}
        style={{ 
          height: '100%', 
          minHeight: '700px', 
          width: '100%', 
          borderBottomLeftRadius: 'var(--bs-border-radius)', 
          borderBottomRightRadius: 'var(--bs-border-radius)' 
        }}
      >
        <TileLayer
          attribution='&copy; OpenStreetMap contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <MapContent {...props} isCtrlPressed={isCtrlPressed} />
      </MapContainer>
    </div>
  );
};

export default MonitoringMap;
