package it.unibo.cas.eventmanagement;

import tools.jackson.databind.ObjectMapper;
import it.unibo.cas.eventmanagement.models.DTOs.AreaDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class JacksonIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testSerialization() throws Exception {
        AreaDTO dto = new AreaDTO();
        dto.setName("test");
        dto.setCapacity(100);
        GeometryFactory gf = new GeometryFactory();
        dto.setBoundary(gf.createPolygon(new Coordinate[]{
            new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 1), new Coordinate(1, 0), new Coordinate(0, 0)
        }));
        
        String json = objectMapper.writeValueAsString(dto);
        System.out.println("JSON IS: " + json);
        
        AreaDTO deserialized = objectMapper.readValue(json, AreaDTO.class);
        assertNotNull(deserialized.getBoundary());
        System.out.println("DESERIALIZED: " + deserialized.getBoundary());
    }
}
