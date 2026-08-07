package it.unibo.cas.eventmanagement.services;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import it.unibo.cas.eventmanagement.exception.ResourceNotFoundException;

import it.unibo.cas.eventmanagement.models.entities.Node;   
import it.unibo.cas.eventmanagement.models.DTOs.NodeDTO;
import it.unibo.cas.eventmanagement.models.DTOs.NodeDistanceDTO;
import it.unibo.cas.eventmanagement.repositories.NodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NodeService {
    
    private static final Logger logger = LoggerFactory.getLogger(NodeService.class);

    @Autowired
    private NodeRepository nodeRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public Node createNode(NodeDTO dto) {

        if (dto == null) {
            throw new IllegalArgumentException("Il payload del nodo non può essere nullo");
        }
        if (dto.getId() == null || dto.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("L'ID del nodo è obbligatorio");
        }
        if (dto.getBrokerUrl() == null || dto.getBrokerUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Il brokerUrl del nodo è obbligatorio");
        }
        if (dto.getLatitude() < -90 || dto.getLatitude() > 90 || dto.getLongitude() < -180 || dto.getLongitude() > 180) {
            throw new IllegalArgumentException("Coordinate geografiche (Lat/Lon) non valide");
        }

        try {
            Node node = Node.builder()
                    .id(dto.getId())
                    .name(dto.getName() != null ? dto.getName() : dto.getId())
                    .type(dto.getType())
                    .brokerUrl(dto.getBrokerUrl())
                    .location(geometryFactory.createPoint(new Coordinate(dto.getLongitude(), dto.getLatitude())))
                    .build();

            Node saved = nodeRepository.save(node);
            logger.info("Nodo registrato con successo: id={}, brokerUrl={}", saved.getId(), saved.getBrokerUrl());
            return saved;
        } catch (Exception e) {
            logger.error("Errore durante il salvataggio del nodo id={}: {}", dto.getId(), e.getMessage());
            throw new RuntimeException("Impossibile salvare il nodo a causa di un errore nel DB: " + e.getMessage(), e);
        }
        
    }

    public List<NodeDTO> getAllNodes() {
        try {
            return nodeRepository.findAll().stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Errore durante il recupero dei nodi: {}", e.getMessage());
            throw new RuntimeException("Impossibile recuperare la lista dei nodi", e);
        }
    }

    public Node getNodeById(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("L'ID del nodo non può essere vuoto");
        }
        return nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Nodo con ID '" + nodeId + "' non trovato"));
    }

    public List<NodeDistanceDTO> getAreaNodeDistances() {
        try {
            List<Object[]> results = nodeRepository.findAllAreaNodeDistances();
            List<NodeDistanceDTO> distances = new ArrayList<>();

            for (Object[] row : results) {
                String areaName = (String) row[0];
                String nodeId = (String) row[1];
                
                if (row[2] == null) {
                    logger.warn("Distanza calcolata nulla tra Area={} e Node={}", areaName, nodeId);
                    continue;
                }

                Double distanceMeters = ((Number) row[2]).doubleValue();
                double latencyMs = calculateIngressLatency(distanceMeters);

                distances.add(NodeDistanceDTO.builder()
                        .areaName(areaName)
                        .nodeId(nodeId)
                        .distanceMeters(Math.round(distanceMeters * 100.0) / 100.0)
                        .estimatedIngressLatencyMs(latencyMs)
                        .build());
            }
            return distances;
        } catch (Exception e) {
            logger.error("Errore durante l'esecuzione della query spaziale PostGIS per le distanze: {}", e.getMessage());
            throw new RuntimeException("Errore nel calcolo delle distanze PostGIS tra Aree e Nodi", e);
        }
    }

    private double calculateIngressLatency(double distanceMeters) {
        // Modello spaziale per il calcolo della latenza : L_base(3ms) + fattore di rete proporzionale alla distanza
        double km = distanceMeters / 1000.0;
        double baseLatencyMs = 3.0;
        double networkFactorMsPerKm = 0.5;
        return Math.round((baseLatencyMs + (km * networkFactorMsPerKm)) * 100.0) / 100.0;
    }


    private NodeDTO convertToDTO(Node node) {
        return NodeDTO.builder()
                .id(node.getId())
                .name(node.getName())
                .type(node.getType())
                .brokerUrl(node.getBrokerUrl())
                .latitude(node.getLocation() != null ? node.getLocation().getY() : 0.0)
                .longitude(node.getLocation() != null ? node.getLocation().getX() : 0.0)
                .build();
    }

}
