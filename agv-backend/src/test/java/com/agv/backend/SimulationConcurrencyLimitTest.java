package com.agv.backend;

import com.agv.backend.service.SimulationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agv.sim.max-concurrent=1",
        "spring.datasource.url=jdbc:h2:mem:agvlimit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class SimulationConcurrencyLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimulationService simulationService;

    @AfterEach
    void stopAll() {
        simulationService.resetAll();
    }

    @Test
    @DisplayName("并发上限为 1 时，第二个用户的启动请求返回 4290 / 429")
    void secondUserIsRejectedWhenLimitReached() throws Exception {
        String admin = login("admin", "admin123");
        String dispatcher = login("dispatcher", "dispatcher123");

        mockMvc.perform(post("/api/sim/start")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roadnetId":1,"taskSetId":1,"algorithm":"nearest","agvCount":1,"seed":101}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        mockMvc.perform(post("/api/sim/start")
                        .header("Authorization", "Bearer " + dispatcher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roadnetId":1,"taskSetId":1,"algorithm":"nearest","agvCount":1,"seed":202}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(4290));

        mockMvc.perform(get("/api/sim/status").header("Authorization", "Bearer " + dispatcher))
                .andExpect(jsonPath("$.data.status").value("IDLE"));
        mockMvc.perform(get("/api/sim/status").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("token").asText();
    }
}
