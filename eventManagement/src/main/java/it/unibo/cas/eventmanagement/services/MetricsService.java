package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.config.LatencyProperties;
import it.unibo.cas.eventmanagement.models.DTOs.SystemMetricsDTO;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.enums.AlertType;
import it.unibo.cas.eventmanagement.orchestration.KubernetesOrchestrationService;
import it.unibo.cas.eventmanagement.repositories.AlertRepository;
import it.unibo.cas.eventmanagement.repositories.AnalysisStatsRepository;
import it.unibo.cas.eventmanagement.repositories.AreaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service that collects, aggregates, and computes global system telemetry metrics.
 *
 * Architectural Role:
 * - Computes simulated network latency for every area under both Slow-Path (database sync) and Fast-Path (alert delivery) modes.
 * - Aggregates the total number of processed analysis windows and active operational alerts.
 * - Queries the Kubernetes orchestration layer to obtain cluster CPU usage and pod placement counts.
 * - Produces the unified SystemMetricsDTO snapshot consumed by the frontend monitoring dashboard.
 */
@Service
public class MetricsService {

    @Autowired
    private LatencyProperties latencyConfig;


    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AnalysisStatsRepository analysisStatsRepository;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private KubernetesOrchestrationService orchestrationService;


    /**
     * Builds a real-time snapshot of system metrics across all monitored areas and compute nodes.
     * Computes Slow-Path and Fast-Path latencies based on node assignments, counts operational alerts,
     * and compiles infrastructure CPU and pod allocation statistics.
     *
     * @return a {@link SystemMetricsDTO} containing the complete operational telemetry snapshot
     */
    public synchronized SystemMetricsDTO getSystemMetrics() {
        List<Area> areas = areaRepository.findAll();
        Map<String, String> assignments = orchestrationService.getCurrentAssignments();

        Map<String, Double> areaSlowLatencies = new HashMap<>();
        Map<String, Double> areaFastLatencies = new HashMap<>();

        double sumSlow = 0.0;
        double sumFast = 0.0;
        int count = 0;

        for (Area area : areas) {
            String currentNode = assignments.getOrDefault(area.getName(), "node-cloud");
            boolean isCloud = "node-cloud".equals(currentNode);

            double lIngresso = nodeService.getIngressLatency(area, currentNode);

            double lUscita = isCloud ? latencyConfig.getExitCloudMs() : latencyConfig.getExitEdgeMs();
            double lSlow = lIngresso + latencyConfig.getTransportMs() + lUscita;
            areaSlowLatencies.put(area.getName(), Math.round(lSlow * 10.0) / 10.0);
            sumSlow += lSlow;

            double lWsNotifica = isCloud ? latencyConfig.getWsWanMs() : latencyConfig.getWsLocalMs();
            double lFast = lIngresso + latencyConfig.getTransportMs() + lWsNotifica;
            areaFastLatencies.put(area.getName(), Math.round(lFast * 10.0) / 10.0);
            sumFast += lFast;

            count++;
        }
        double avgSlow = count > 0 ? Math.round((sumSlow / count) * 10.0) / 10.0 : 0.0;
        double avgFast = count > 0 ? Math.round((sumFast / count) * 10.0) / 10.0 : 0.0;

        long totalReqs = analysisStatsRepository.count();

        long operationalAlerts = alertRepository.countByAlertTypeIn(
                List.of(AlertType.MANUAL, AlertType.AUTOMATIC)
        );

        return new SystemMetricsDTO(
                totalReqs,
                avgSlow,
                avgFast,
                operationalAlerts,
                areaSlowLatencies,
                areaFastLatencies,
                orchestrationService.getNodeCpuUsagePercentageMap(),
                orchestrationService.getNodePodCountMap()
        );
    }
}