import { useState, useCallback } from 'react';
import type { AreaDTO, NodeDTO } from '../types';
import { areaApi } from '../api/areaApi';
import { nodeApi } from '../api/nodeApi';

export function useMapInteractions(refreshData: () => void) {
  const [showAreaModal, setShowAreaModal] = useState(false);
  const [showNodeModal, setShowNodeModal] = useState(false);
  
  const [currentAreaCoords, setCurrentAreaCoords] = useState<number[][][] | null>(null);
  const [currentNodeCoords, setCurrentNodeCoords] = useState<{lat: number, lng: number} | null>(null);
  const [currentLayer, setCurrentLayer] = useState<L.Layer | null>(null);

  const [manualAlertMode, setManualAlertMode] = useState(false);
  const [manualAlertLocation, setManualAlertLocation] = useState<{lat: number, lng: number} | null>(null);

  const handleAreaDraw = useCallback((coords: number[][][], layer: L.Layer) => {
    setCurrentAreaCoords(coords);
    setCurrentLayer(layer);
    setShowAreaModal(true);
  }, []);

  const handleNodeDraw = useCallback((lat: number, lng: number, layer: L.Layer) => {
    setCurrentNodeCoords({ lat, lng });
    setCurrentLayer(layer);
    setShowNodeModal(true);
  }, []);

  const handleAreaEdit = useCallback(async (id: string, coords: number[][], dto: AreaDTO) => {
    try {
      const updatedArea = { ...dto, boundary: { type: "Polygon" as const, coordinates: [coords] } };
      await areaApi.updateArea(id, updatedArea);
      refreshData();
    } catch (e) {
      console.error("Errore aggiornamento area", e);
      refreshData();
    }
  }, [refreshData]);

  const handleNodeEdit = useCallback(async (id: string, lat: number, lng: number, dto: NodeDTO) => {
    try {
      const updatedNode = { ...dto, latitude: lat, longitude: lng };
      await nodeApi.updateNode(id, updatedNode);
      refreshData();
    } catch (e) {
      console.error("Errore aggiornamento nodo", e);
      refreshData();
    }
  }, [refreshData]);

  const handleAreaDelete = useCallback(async (id: string) => {
    try {
      await areaApi.deleteArea(id);
      refreshData();
    } catch (e) {
      console.error("Errore cancellazione area", e);
      refreshData();
    }
  }, [refreshData]);

  const handleSaveArea = async (area: AreaDTO) => {
    try {
      await areaApi.createArea(area);
      setShowAreaModal(false);
      if (currentLayer) currentLayer.remove();
      refreshData();
    } catch (e) {
      alert("Errore salvataggio area");
    }
  };

  const handleSaveNode = async (node: NodeDTO) => {
    try {
      if (node.id) {
        await nodeApi.updateNode(node.id, node); // Esegue PUT /api/nodes/{id}
      } else {
        await nodeApi.createNode(node);
      }
      setShowNodeModal(false);
      await refreshData();
    } catch (error) {
      console.error('Errore nel salvataggio del nodo:', error);
      alert('Errore durante l\'aggiornamento della posizione del nodo.');
    }
  };

  const handleCancel = () => {
    if (currentLayer) {
      currentLayer.remove();
    }
    setShowAreaModal(false);
    setShowNodeModal(false);
  };

  return {
    showAreaModal,
    showNodeModal,
    currentAreaCoords,
    currentNodeCoords,
    manualAlertMode,
    setManualAlertMode,
    manualAlertLocation,
    setManualAlertLocation,
    handleAreaDraw,
    handleNodeDraw,
    handleAreaEdit,
    handleNodeEdit,
    handleAreaDelete,
    handleSaveArea,
    handleSaveNode,
    handleCancel
  };
}
