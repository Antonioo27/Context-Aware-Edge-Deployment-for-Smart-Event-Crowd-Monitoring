package it.unibo.cas.eventmanagement.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import it.unibo.cas.eventmanagement.services.NodeService;
import it.unibo.cas.eventmanagement.models.DTOs.NodeDTO;
import it.unibo.cas.eventmanagement.models.entities.Node;
import it.unibo.cas.eventmanagement.models.DTOs.NodeDistanceDTO;

@RestController
@RequestMapping("/api/nodes")
public class NodeController {
    
    @Autowired private NodeService nodeService;

    @PostMapping
    public ResponseEntity<Node> createNode(@RequestBody NodeDTO nodeDTO) {
        Node created = nodeService.createNode(nodeDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
