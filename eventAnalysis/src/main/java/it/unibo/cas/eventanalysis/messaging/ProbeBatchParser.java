package it.unibo.cas.eventanalysis.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import it.unibo.cas.eventanalysis.exception.InvalidBatchException;
import it.unibo.cas.eventanalysis.models.entities.Probe;
import it.unibo.cas.eventanalysis.models.entities.ProbeBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProbeBatchParser {

    private final ObjectMapper objectMapper;

    public ProbeBatch parseBatch(byte[] payload, String expectedArea, OffsetDateTime receivedAt, int mid, int qos) throws InvalidBatchException {
        JsonNode data;
        try {
            data = objectMapper.readTree(payload);
        } catch (Exception exc) {
            throw new InvalidBatchException("payload is not valid JSON UTF-8: " + exc.getMessage(), exc);
        }

        if (!data.isObject()) {
            throw new InvalidBatchException("payload is not a JSON object");
        }

        String[] requiredFields = { "area_id", "batch_id", "sent_at", "probes" };
        for (String field : requiredFields) {
            if (!data.has(field)) {
                throw new InvalidBatchException("missing field: " + field);
            }
        }

        String areaId = data.get("area_id").asText();
        if (expectedArea != null && !areaId.equals(expectedArea)) {
            throw new InvalidBatchException(String.format("area_id %s does not match %s", areaId, expectedArea));
        }

        long batchId;
        OffsetDateTime sentAt;
        try {
            batchId = data.get("batch_id").asLong();
            sentAt = parseIso8601(data.get("sent_at").asText());
        } catch (Exception exc) {
            throw new InvalidBatchException("invalid batch_id or sent_at field: " + exc.getMessage(), exc);
        }

        JsonNode rawProbes = data.get("probes");
        if (!rawProbes.isArray()) {
            throw new InvalidBatchException("probes field is not a list");
        }

        List<Probe> probes = new ArrayList<>();
        int malformedProbes = 0;
        ArrayNode probesArray = (ArrayNode) rawProbes;

        for (JsonNode item : probesArray) {
            try {
                String sensorId = item.get("sensor_id").asText();
                OffsetDateTime ts = parseIso8601(item.get("ts").asText());
                String mac = item.get("mac").asText();
                int rssi = item.get("rssi").asInt();
                probes.add(new Probe(sensorId, ts, mac, rssi));
            } catch (Exception exc) {
                malformedProbes++;
            }
        }

        int countDeclared = data.has("count") ? data.get("count").asInt() : probes.size();

        return ProbeBatch.builder()
                .areaId(areaId)
                .batchId(batchId)
                .sentAt(sentAt)
                .receivedAt(receivedAt)
                .countDeclared(countDeclared)
                .probes(probes)
                .malformedProbes(malformedProbes)
                .mid(mid)
                .qos(qos)
                .build();
    }

    private OffsetDateTime parseIso8601(String value) {
        if (value.endsWith("Z")) {
            value = value.substring(0, value.length() - 1) + "+00:00";
        }
        return OffsetDateTime.parse(value);
    }
}
