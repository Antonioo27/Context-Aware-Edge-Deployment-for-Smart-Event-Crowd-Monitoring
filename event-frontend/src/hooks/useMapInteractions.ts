/**
 * Custom React hook for orchestrating map interactions and Leaflet draw operations.
 * Coordinates modal state, geometric coordinates, and API persistence for creating,
 * editing, or deleting monitoring areas and cluster nodes, as well as handling manual alert pinpoints.
 */

import { useState, useCallback } from 'react';
import type { AreaDTO, NodeDTO } from '../types';
import { areaApi } from '../api/areaApi';
import { nodeApi } from '../api/nodeApi';

/**
 * Manages map interaction state, modal dialogs, and backend synchronization callbacks for GIS operations.
 *
 * @param refreshData Callback function invoked to refresh dashboard datasets after create/update/delete operations.
 * @returns An object providing UI state flags, active coordinates, manual alert mode flags, and handler callbacks.
 */
export function useMapInteractions(refreshData: () => void) {
  const [showAreaModal, setShowAreaModal] = useState(false);
  const [showNodeModal, setShowNodeModal] = useState(false);
  
  const [currentAreaCoords, setCurrentAreaCoords] = useState<number[][][] | null>(null);
  const [currentNodeCoords, setCurrentNodeCoords] = useState<{lat: number, lng: number} | null>(null);
  const [currentLayer, setCurrentLayer] = useState<L.Layer | null>(null);

  const [manualAlertMode, setManualAlertMode] = useState(false);
  const [manualAlertLocation, setManualAlertLocation] = useState<{lat: number, lng: number} | null>(null);

  /**
   * Handles completion of an area polygon draw on the map, opening the area creation modal.
   *
   * @param coords GeoJSON polygon coordinates [lng, lat][][].
   * @param layer The created Leaflet layer reference for cleanup if canceled.
   */
  const handleAreaDraw = useCallback((coords: number[][][], layer: L.Layer) => {
    setCurrentAreaCoords(coords);
    setCurrentLayer(layer);
    setShowAreaModal(true);
  }, []);

  /**
   * Handles placement of a node marker on the map, opening the node configuration modal.
   *
   * @param lat Marker latitude.
   * @param lng Marker longitude.
   * @param layer The created Leaflet marker layer reference.
   */
  const handleNodeDraw = useCallback((lat: number, lng: number, layer: L.Layer) => {
    setCurrentNodeCoords({ lat, lng });
    setCurrentLayer(layer);
    setShowNodeModal(true);
  }, []);

  /**
   * Handles boundary polygon modifications for an existing area and persists updates to the API.
   *
   * @param id The identifier or name of the area being updated.
   * @param coords The updated polygon vertex coordinates.
   * @param dto The current area data transfer object.
   */
  const handleAreaEdit = useCallback(async (id: string, coords: number[][], dto: AreaDTO) => {
    try {
      const updatedArea = { ...dto, boundary: { type: 'Polygon' as const, coordinates: [coords] } };
      await areaApi.updateArea(id, updatedArea);
      refreshData();
    } catch (e) {
      console.error('Error updating area boundary:', e);
      refreshData();
    }
  }, [refreshData]);

  /**
   * Handles position modifications for an existing infrastructure node and updates the backend.
   *
   * @param id The node identifier.
   * @param lat Updated latitude coordinate.
   * @param lng Updated longitude coordinate.
   * @param dto The current node data transfer object.
   */
  const handleNodeEdit = useCallback(async (id: string, lat: number, lng: number, dto: NodeDTO) => {
    try {
      const updatedNode = { ...dto, latitude: lat, longitude: lng };
      await nodeApi.updateNode(id, updatedNode);
      refreshData();
    } catch (e) {
      console.error('Error updating node position:', e);
      refreshData();
    }
  }, [refreshData]);

  /**
   * Deletes a specified monitoring area from the backend.
   *
   * @param id The area identifier or name to remove.
   */
  const handleAreaDelete = useCallback(async (id: string) => {
    try {
      await areaApi.deleteArea(id);
      refreshData();
    } catch (e) {
      console.error('Error deleting area:', e);
      refreshData();
    }
  }, [refreshData]);

  /**
   * Persists a newly defined area entity to the backend and cleans up the temporary drawing layer.
   *
   * @param area The area payload to save.
   */
  const handleSaveArea = async (area: AreaDTO) => {
    try {
      await areaApi.createArea(area);
      setShowAreaModal(false);
      if (currentLayer) currentLayer.remove();
      refreshData();
    } catch (e) {
      alert('Error saving area');
    }
  };

  /**
   * Persists node location updates or creations to the backend and triggers data refresh.
   *
   * @param node The node payload containing updated geographic coordinates.
   */
  const handleSaveNode = async (node: NodeDTO) => {
    try {
      if (node.id) {
        await nodeApi.updateNode(node.id, node);
      } else {
        await nodeApi.createNode(node);
      }
      setShowNodeModal(false);
      await refreshData();
    } catch (error) {
      console.error('Error saving node position:', error);
      alert('Error updating node position.');
    }
  };

  /**
   * Cancels active creation workflows, removes temporary map drawing layers, and closes open modals.
   */
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
