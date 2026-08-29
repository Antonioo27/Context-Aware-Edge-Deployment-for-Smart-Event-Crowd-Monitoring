package it.unibo.cas.eventmanagement.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.client.KubernetesClient;
import it.unibo.cas.eventmanagement.config.LatencyProperties;
import it.unibo.cas.eventmanagement.exception.ResourceNotFoundException;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.entities.Node;
import it.unibo.cas.eventmanagement.models.enums.NodeType;
import it.unibo.cas.eventmanagement.orchestration.KubernetesOrchestrationService;
import it.unibo.cas.eventmanagement.models.DTOs.NodeDTO;
import it.unibo.cas.eventmanagement.models.DTOs.NodeDistanceDTO;
import it.unibo.cas.eventmanagement.repositories.NodeRepository;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NodeService {
    
    private static final Logger logger = LoggerFactory.getLogger(NodeService.class);

    @Autowired
    private LatencyProperties latencyConfig;

    @Autowired
    private NodeRepository nodeRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    private KubernetesClient kubernetesClient;

    @Autowired
    private KubernetesOrchestrationService kubernetesOrchestrationService;

    /**
    * Interroga l'API Server di Kubernetes per scoprire i nodi del cluster
    * e creare/aggiornare le bozze dei nodi logici nel DB postgres
    */
    @Transactional
    public List<NodeDTO> syncNodesFromKubernetes() {
        logger.info("Avvio auto-discovery nodi dal cluster Kubernetes...");    
        
        try {
            List<io.fabric8.kubernetes.api.model.Node> k8sNodes = kubernetesClient.nodes().list().getItems();
            List<NodeDTO> syncedNodes = new ArrayList<>();

            for (io.fabric8.kubernetes.api.model.Node k8sNode : k8sNodes) {
                String k8sNodeName = k8sNode.getMetadata().getName();
                Map<String, String> labels = k8sNode.getMetadata().getLabels();
                if (labels == null) {
                    labels = Collections.emptyMap();
                }

                String nodeId = labels.getOrDefault("node-id", k8sNodeName);
                String tierLabel = labels.getOrDefault("tier", labels.getOrDefault("node-role", "EDGE"));
                NodeType nodeType = tierLabel.equalsIgnoreCase("CLOUD") ? NodeType.CLOUD : NodeType.EDGE;


                // Estrazione dinamica dell'ip reale del nodo k8s
                String nodeIp = null;
                if (k8sNode.getStatus() != null && k8sNode.getStatus().getAddresses() != null) {
                    for (var addr : k8sNode.getStatus().getAddresses()) {
                        // Priorità all'ExternalIP se presente, altrimenti usiamo l'InternalIP
                        if ("ExternalIP".equalsIgnoreCase(addr.getType()) && addr.getAddress() != null) {
                            nodeIp = addr.getAddress();
                            break; 
                        } else if ("InternalIP".equalsIgnoreCase(addr.getType()) && nodeIp == null) {
                            nodeIp = addr.getAddress();
                        }
                    }
                }

                // Fallback di sicurezza in locale
                if (nodeIp == null || nodeIp.isBlank()) {
                    nodeIp = "127.0.0.1";
                    logger.warn("Impossibile recuperare l'IP per il nodo K8s {}. Uso fallback: 127.0.0.1", nodeId);
                }

                // Costruzione dell'URL del broker Mosquitto esposto sul nodo
                String brokerUrl = "tcp://" + nodeIp + ":1883";
                
                Optional<Node> existing = nodeRepository.findById(nodeId);
                Node node;

                if (existing.isPresent()) {
                    node = existing.get();
                    node.setType(nodeType);
                    node.setBrokerUrl(brokerUrl);
                    logger.info("Aggiornato nodo K8s esistente: id={}, type={}", nodeId, nodeType);                
                } else {
                    node = Node.builder()
                        .id(nodeId)
                        .name("Nodo " + nodeType + " (" + nodeId + ")")
                        .type(nodeType)
                        .brokerUrl(brokerUrl)
                        .location(geometryFactory.createPoint(new Coordinate(0.0, 0.0))) // Coord temporanee da mappa
                        .build();
                    logger.info("Scoperto nuovo nodo K8s: id={}, type={}, brokerUrl={}", nodeId, nodeType, brokerUrl);
            }

                Node saved = nodeRepository.save(node);
                syncedNodes.add(convertToDTO(saved));
            }

            return syncedNodes;
        }
        catch (Exception e) {
            logger.error("Errore durante la sincronizzazione con Kubernetes: {}", e.getMessage());
            throw new RuntimeException("Impossibile effettuare la discovery dei nodi da Kubernetes: " + e.getMessage(), e);
        }
    }

    /**
     * Aggiorna la posizione geografica per un nodo logico
     * 
     * 
     */
    @Transactional
    public NodeDTO updateNodeLocationAndBroker(String nodeId, NodeDTO dto) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Nodo logico '" + nodeId + "' non trovato nel DB"));

        if (dto == null) {
            throw new IllegalArgumentException("Il payload inviato è nullo");
        }

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            node.setName(dto.getName());
        }
        
        if (dto.getBrokerUrl() != null && !dto.getBrokerUrl().trim().isEmpty()) {
            node.setBrokerUrl(dto.getBrokerUrl());
        }

        // Aggiorna il punto geometrico PostGIS solo se le coordinate sono fornite
        if (dto.getLatitude() < -90 || dto.getLatitude() > 90 || dto.getLongitude() < -180 || dto.getLongitude() > 180) {
            throw new IllegalArgumentException("Coordinate Lat/Lon fuori dal range valido");
        }
        
        // Aggiorna il punto geometrico PostGIS
        node.setLocation(geometryFactory.createPoint(new Coordinate(dto.getLongitude(), dto.getLatitude())));
        Node updated = nodeRepository.save(node);
        
        logger.info("Coordinate e Broker aggiornati per il nodo {}: Lat={}, Lon={}, Broker={}", 
                nodeId, dto.getLatitude(), dto.getLongitude(), dto.getBrokerUrl());

        return convertToDTO(updated);
    }


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
                double latencyMs = calculateIngressLatency(nodeId, distanceMeters);

                distances.add(NodeDistanceDTO.builder()
                        .areaName(areaName)
                        .nodeId(nodeId)
                        .distanceMeters(Math.round(distanceMeters * 100.0) / 100.0)
                        .estimatedIngressLatencyMs(Math.round(latencyMs * 100.0) / 100.0)
                        .build());
            }
            return distances;
        } catch (Exception e) {
            logger.error("Errore durante l'esecuzione della query spaziale PostGIS per le distanze: {}", e.getMessage());
            throw new RuntimeException("Errore nel calcolo delle distanze PostGIS tra Aree e Nodi", e);
        }
    }

    public String getClosestIdForArea(Area area) {
        try {            
            return nodeRepository.findClosestEdgeNodeId(area.getBoundary())
                            .orElse("node-cloud"); // Fallback sul Cloud se non ci sono nodi Edge
        } catch (Exception e) {
            logger.error("Errore durante l'esecuzione della query spaziale PostGIS trovare nodo più vicino ad un'area: {}", e.getMessage());
            throw new RuntimeException("Errore nel calcolo della ricerca distanza minore nodo area", e);
        }    
    
    }

    public double getIngressLatency(Area area, String nodeId) {
        // 1. Caso Nodo Cloud -> Latenza WAN fissa di ingresso (40 ms)
        if ("node-cloud".equals(nodeId) || (nodeId != null && nodeId.toLowerCase().contains("cloud"))) {
            return latencyConfig.getCloudIngressMs();
        }

        try {
            // 2. Calcolo distanza geospaziale diretta da PostGIS
            Double distanceMeters = nodeRepository.findDistanceBetweenAreaAndNode(area.getName(), nodeId);

            if (distanceMeters != null) {
                return calculateIngressLatency(nodeId, distanceMeters);
            }
        } catch (Exception e) {
            logger.error("Errore nel calcolo della latenza di ingresso per Area '{}' e Nodo '{}': {}",
                    area.getName(), nodeId, e.getMessage());
        }

        // 3. Fallback di sicurezza: se la query fallisce consideriamo latenza WAN
        return latencyConfig.getCloudIngressMs();
    }

    public Map<String, List<String>> getNodePodAllocations() {
        Map<String, List<String>> allocationMap = new HashMap<>();
        
        // Inizializza la mappa con tutti i nodi censiti (lista vuota)
        List<Node> allNodes = nodeRepository.findAll();
        for (Node n : allNodes) {
            allocationMap.put(n.getId(), new ArrayList<>());
        }
        // Assicurati che esista anche la chiave node-cloud
        allocationMap.putIfAbsent("node-cloud", new ArrayList<>());

        try {
            // Recupera tutti i Pod del namespace
            var pods = kubernetesClient.pods().inNamespace(kubernetesClient.getNamespace()).list().getItems();
            for (var pod : pods) {
                String podName = pod.getMetadata().getName();
                
                // Filtra solo i pod dei servizi di analisi
                if (podName.startsWith("event-analysis-")) {
                    // 1. Prova a leggere il node-id dal nodeSelector
                    String targetNodeId = null;
                    if (pod.getSpec() != null && pod.getSpec().getNodeSelector() != null) {
                        targetNodeId = pod.getSpec().getNodeSelector().get("node-id");
                    }
                    
                    // 2. Se non presente nel nodeSelector, ricava il nodo fisico reale di K8s
                    if (targetNodeId == null && pod.getSpec() != null && pod.getSpec().getNodeName() != null) {
                        String k8sNodeName = pod.getSpec().getNodeName();
                        var k8sNode = kubernetesClient.nodes().withName(k8sNodeName).get();
                        if (k8sNode != null && k8sNode.getMetadata().getLabels() != null) {
                            targetNodeId = k8sNode.getMetadata().getLabels().get("node-id");
                        }
                        if (targetNodeId == null) {
                            targetNodeId = k8sNodeName;
                        }
                    }

                    if (targetNodeId == null) {
                        targetNodeId = "node-cloud";
                    }

                    allocationMap.computeIfAbsent(targetNodeId, k -> new ArrayList<>()).add(podName);
                }
            }
        } catch (Exception e) {
            logger.error("Errore nel recupero delle allocazioni Pod da K8s: {}", e.getMessage());
        }

        return allocationMap;
    }

    public Map<String, Double> getNodeCpuMetrics() {
        return kubernetesOrchestrationService.getNodeCpuUsagePercentageMap();
    }

    
    private double calculateIngressLatency(String nodeId, double distanceMeters) {
        if ("node-cloud".equals(nodeId)) {
            return latencyConfig.getCloudIngressMs();
        }
        // Base 1.0 ms + propagazione metrica
        return latencyConfig.getBaseEdgeMs() + (distanceMeters * latencyConfig.getMsPerMeter());
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
