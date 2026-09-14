package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.MigrationDTO;
import it.unibo.cas.eventmanagement.models.entities.Migration;
import it.unibo.cas.eventmanagement.repositories.MigrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that manages pod migration history and audit records in the database.
 *
 * Architectural Role:
 * - Persists workload relocation decisions triggered by the orchestration control loop.
 * - Tracks source and destination nodes, placement costs, reasons, and Kubernetes execution status.
 * - Provides query methods used by REST endpoints to display migration logs in the web dashboard.
 * - Supports batch cleanup of migration history to reset test scenarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private final MigrationRepository migrationRepository;

    @Transactional
    public Migration recordMigration(String areaId, String podName, String fromNode, String toNode, Double previousCost, Double newCost, 
                                    String reason, boolean success, String errorMessage) 
    {


        Migration migration = Migration.builder()
                .areaId(areaId)
                .podName(podName)
                .fromNode(fromNode)
                .toNode(toNode)
                .previousCost(previousCost)
                .newCost(newCost)
                .reason(reason)
                .success(success)
                .errorMessage(errorMessage)
                .timestamp(OffsetDateTime.now())
                .build();

        Migration saved = migrationRepository.save(migration);
        log.info("[MIGRATION RECORDED] id={} | Area={} | {} -> {} | Reason: {}", 
                saved.getId(), areaId, fromNode, toNode, reason);

        return saved;
    }

    public List<MigrationDTO> getAllMigrations() {
        return migrationRepository.findAllByOrderByTimestampDesc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<MigrationDTO> getMigrationsByArea(String areaId) {
        return migrationRepository.findByAreaIdOrderByTimestampDesc(areaId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }


    private MigrationDTO toDTO(Migration m) {
        return MigrationDTO.builder()
                .id(m.getId())
                .areaId(m.getAreaId())
                .podName(m.getPodName())
                .fromNode(m.getFromNode())
                .toNode(m.getToNode())
                .previousCost(m.getPreviousCost())
                .newCost(m.getNewCost())
                .reason(m.getReason())
                .success(m.getSuccess())
                .errorMessage(m.getErrorMessage())
                .timestamp(m.getTimestamp())
                .build();
    }

    @Transactional
    public void deleteAllMigrations() {
        long count = migrationRepository.count();
        migrationRepository.deleteAllInBatch(); 
        log.warn("[MIGRATION SERVICE] Eliminate tutte le {} migrazioni dal database.", count);
    }
}