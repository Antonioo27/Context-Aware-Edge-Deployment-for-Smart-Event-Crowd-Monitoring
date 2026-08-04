package it.unibo.cas.eventmanagement.utils;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.core.JacksonException;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;

public class PolygonDeserializer extends ValueDeserializer<Polygon> {
    @Override
    public Polygon deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        JsonNode node = ctxt.readTree(p);
        
        JsonNode coordinatesNode = node.get("coordinates");
        if (coordinatesNode == null || !coordinatesNode.isArray() || coordinatesNode.isEmpty()) {
            return null;
        }
        
        JsonNode linearRing = coordinatesNode.get(0);
        Coordinate[] coords = new Coordinate[linearRing.size()];
        for (int i = 0; i < linearRing.size(); i++) {
            JsonNode point = linearRing.get(i);
            coords[i] = new Coordinate(point.get(0).asDouble(), point.get(1).asDouble());
        }
        return new GeometryFactory().createPolygon(coords);
    }
}
