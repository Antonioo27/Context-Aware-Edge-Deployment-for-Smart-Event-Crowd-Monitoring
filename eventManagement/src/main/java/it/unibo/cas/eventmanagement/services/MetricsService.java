package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.config.LatencyProperties;
import it.unibo.cas.eventmanagement.models.DTOs.SystemMetricsDTO;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.orchestration.KubernetesOrchestrationService;
import it.unibo.cas.eventmanagement.repositories.AlertRepository;
import it.unibo.cas.eventmanagement.repositories.AnalysisStatsRepository;
import it.unibo.cas.eventmanagement.repositories.AreaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    @Autowired
    private LatencyProperties latencyConfig;

    private Double currentEmaSlowPath = null;
    private Double currentEmaFastPath = null;
    private final AtomicLong totalRequestsCounter = new AtomicLong(0);

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



    public void incrementRequestCounter() {
        totalRequestsCounter.incrementAndGet();
    }

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

            // 1. Tratta Ingress (PostGIS o WAN)
            double lIngresso = nodeService.getIngressLatency(area, currentNode);

            // 2. Slow-Path (Persistenza DB Cloud)
            double lUscita = isCloud ? latencyConfig.getExitCloudMs() : latencyConfig.getExitEdgeMs();
            double lSlow = lIngresso + latencyConfig.getTransportMs() + lUscita;
            areaSlowLatencies.put(area.getName(), Math.round(lSlow * 10.0) / 10.0);
            sumSlow += lSlow;

            // 3. Fast-Path (Notifica reattiva Alert)
            double lWsNotifica = isCloud ? latencyConfig.getWsWanMs() : latencyConfig.getWsLocalMs();
            double lFast = lIngresso + latencyConfig.getTransportMs() + lWsNotifica;
            areaFastLatencies.put(area.getName(), Math.round(lFast * 10.0) / 10.0);
            sumFast += lFast;

            count++;
        }
        double avgSlow = count > 0 ? Math.round((sumSlow / count) * 10.0) / 10.0 : 0.0;
        double avgFast = count > 0 ? Math.round((sumFast / count) * 10.0) / 10.0 : 0.0;

        long totalReqs = Math.max(totalRequestsCounter.get(), analysisStatsRepository.count());

        return new SystemMetricsDTO(
                totalReqs,
                avgSlow,
                avgFast,
                alertRepository.count(),
                areaSlowLatencies,
                areaFastLatencies,
                orchestrationService.getNodeCpuUsagePercentageMap(),
                orchestrationService.getNodePodCountMap()
        );
    }



}