package it.unibo.cas.eventmanagement.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import it.unibo.cas.eventmanagement.services.NodeService;
import it.unibo.cas.eventmanagement.models.DTOs.NodeDTO;
import it.unibo.cas.eventmanagement.models.entities.Node;
import it.unibo.cas.eventmanagement.orchestration.KubernetesOrchestrationService;
import it.unibo.cas.eventmanagement.models.DTOs.NodeDistanceDTO;

@RestController
@RequestMapping("/api/nodes")
public class NodeController {
    
    @Autowired private NodeService nodeService;

    @Autowired private KubernetesOrchestrationService orchestrationService;


    @PostMapping("/sync-k8s")
    public ResponseEntity<List<NodeDTO>> syncFromKubernetes() {
        List<NodeDTO> nodes = nodeService.syncNodesFromKubernetes();
        return ResponseEntity.ok(nodes);
    }

    @PostMapping
    public ResponseEntity<NodeDTO> createNode(@RequestBody NodeDTO nodeDTO) {
        Node created = nodeService.createNode(nodeDTO);
        NodeDTO dto = NodeDTO.builder()
                .id(created.getId())
                .name(created.getName())
                .type(created.getType())
                .brokerUrl(created.getBrokerUrl())
                .latitude(created.getLocation() != null ? created.getLocation().getY() : 0.0)
                .longitude(created.getLocation() != null ? created.getLocation().getX() : 0.0)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public ResponseEntity<List<NodeDTO>> getAllNodes() {
        return ResponseEntity.ok(nodeService.getAllNodes());
    }

    @GetMapping("/distances")
    public ResponseEntity<List<NodeDistanceDTO>> getAreaNodeDistances() {
        return ResponseEntity.ok(nodeService.getAreaNodeDistances());
    }

    @GetMapping("/allocations")
    public ResponseEntity<Map<String, List<String>>> getNodePodAllocations() {
        return ResponseEntity.ok(nodeService.getNodePodAllocations());
    }

    @GetMapping("/nodeUsage")
    public ResponseEntity<Map<String, Double>> getNodeCpuMetrics() {
        return ResponseEntity.ok(nodeService.getNodeCpuMetrics());
    }

    @GetMapping("/{id}")
    public ResponseEntity<NodeDTO> getNodeById(@PathVariable String id) {
        Node node = nodeService.getNodeById(id);
        NodeDTO dto = NodeDTO.builder()
                .id(node.getId())
                .name(node.getName())
                .type(node.getType())
                .brokerUrl(node.getBrokerUrl())
                .latitude(node.getLocation() != null ? node.getLocation().getY() : 0.0)
                .longitude(node.getLocation() != null ? node.getLocation().getX() : 0.0)
                .build();
        return ResponseEntity.ok(dto);
    }

    /**
     * Aggiorna le coordinate GPS (Lat, Lon) e il brokerUrl del nodo logico.
     * Usato dalla Dashboard quando l'utente posiziona il nodo sulla mappa.
     * Non viene usato dall'utente per aggiornare il broker
     */
    @PutMapping("/{id}")
    public ResponseEntity<NodeDTO> updateNode(@PathVariable String id, @RequestBody NodeDTO nodeDTO) {
        NodeDTO updated = nodeService.updateNodeLocationAndBroker(id, nodeDTO);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{nodeId}/stress")
    public ResponseEntity<Map<String, Object>> triggerCpuStress(
            @PathVariable String nodeId,
            @RequestParam(defaultValue = "180") int duration) {
        
        boolean started = orchestrationService.startNodeCpuStress(nodeId, duration);
        return ResponseEntity.ok(Map.of(
                "nodeId", nodeId,
                "stressActive", started,
                "durationSeconds", duration,
                "message", started ? "Stress test CPU avviato per " + duration + "s" : "Impossibile avviare lo stress test"
        ));
    }

    @DeleteMapping("/{nodeId}/stress")
    public ResponseEntity<Map<String, Object>> stopCpuStress(@PathVariable String nodeId) {
        boolean stopped = orchestrationService.stopNodeCpuStress(nodeId);
        return ResponseEntity.ok(Map.of(
                "nodeId", nodeId,
                "stressActive", false,
                "message", stopped ? "Stress test interrotto" : "Nessun pod di stress attivo trovato"
        ));
    }

    @PostMapping("/{nodeId}/cordon")
    public ResponseEntity<Map<String, Object>> toggleCordon(
            @PathVariable String nodeId,
            @RequestParam boolean cordon) {
        
        boolean success = orchestrationService.setNodeCordon(nodeId, cordon);
        return ResponseEntity.ok(Map.of(
                "nodeId", nodeId,
                "cordoned", cordon,
                "success", success,
                "message", cordon ? "Nodo spento/cordonato con successo" : "Nodo ripristinato/uncordon con successo"
        ));
    }

    @GetMapping("/simulation-status")
    public ResponseEntity<Map<String, List<String>>> getSimulationStatus() {
        List<String> stressedNodes = orchestrationService.getStressedNodeIds();
        List<String> cordonedNodes = orchestrationService.getCordonedNodeIds();
        
        return ResponseEntity.ok(Map.of(
                "stressedNodes", stressedNodes,
                "cordonedNodes", cordonedNodes
        ));
    }

}
