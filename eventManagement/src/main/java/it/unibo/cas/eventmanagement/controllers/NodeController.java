package it.unibo.cas.eventmanagement.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.*;
import it.unibo.cas.eventmanagement.services.NodeService;
import it.unibo.cas.eventmanagement.models.DTOs.NodeDTO;
import it.unibo.cas.eventmanagement.models.entities.Node;
import it.unibo.cas.eventmanagement.models.DTOs.NodeDistanceDTO;

@RestController
@RequestMapping("/api/nodes")
public class NodeController {
    
    @Autowired private NodeService nodeService;

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


    @GetMapping
    public ResponseEntity<List<NodeDTO>> getAllNodes() {
        return ResponseEntity.ok(nodeService.getAllNodes());
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

    @GetMapping("/distances")
    public ResponseEntity<List<NodeDistanceDTO>> getAreaNodeDistances() {
        return ResponseEntity.ok(nodeService.getAreaNodeDistances());
    }
}
