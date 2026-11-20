package com.agv.backend;

import com.agv.backend.dto.RoadNetPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetPayloadZoneTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void oldPayloadWithoutZonesStillParses() throws Exception {
        RoadNetPayload payload = mapper.readValue("""
                {"nodes":[],"edges":[],"width":50,"height":34}
                """, RoadNetPayload.class);

        assertEquals(50, payload.width());
        assertEquals(34, payload.height());
    }

    @Test
    void zonesRoundTrip() throws Exception {
        String json = """
                {"nodes":[],"edges":[],"width":50,"height":34,"zones":[
                  {"id":"Z1","name":"充电区","type":"CHARGING","x":1,"y":2,"width":6,"height":4}
                ]}
                """;

        RoadNetPayload payload = mapper.readValue(json, RoadNetPayload.class);
        JsonNode tree = mapper.readTree(mapper.writeValueAsString(payload));

        assertTrue(tree.has("zones"));
        assertTrue(tree.get("zones").isArray());
        assertEquals(1, tree.get("zones").size());
        assertEquals("Z1", tree.get("zones").get(0).get("id").asText());
        assertEquals("充电区", tree.get("zones").get(0).get("name").asText());
        assertEquals("CHARGING", tree.get("zones").get(0).get("type").asText());
        assertFalse(tree.get("zones").get(0).get("width").isMissingNode());
        assertEquals(6, tree.get("zones").get(0).get("width").asDouble());
    }
}
