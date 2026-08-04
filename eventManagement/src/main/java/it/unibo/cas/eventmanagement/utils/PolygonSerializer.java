package it.unibo.cas.eventmanagement.utils;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import tools.jackson.core.JacksonException;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Coordinate;

public class PolygonSerializer extends ValueSerializer<Polygon> {
    @Override
    public void serialize(Polygon value, JsonGenerator gen, SerializationContext serializers) throws JacksonException {
        gen.writeStartObject();
        gen.writeName("type");
        gen.writeString("Polygon");
        gen.writeName("coordinates");
        gen.writeStartArray();
        gen.writeStartArray();
        for (Coordinate coord : value.getCoordinates()) {
            gen.writeStartArray();
            gen.writeNumber(coord.x);
            gen.writeNumber(coord.y);
            gen.writeEndArray();
        }
        gen.writeEndArray();
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
