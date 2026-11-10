package com.agv.backend;

import com.agv.backend.repository.ExperimentMetricRepository;
import com.agv.backend.repository.ExperimentRepository;
import com.agv.backend.service.SimulationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MultiUserSimulationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentMetricRepository metricRepository;

    @BeforeEach
    void resetState() {
        simulationService.resetAll();
        metricRepository.deleteAll();
        experimentRepository.deleteAll();
    }

    @AfterEach
    void stopAll() {
        simulationService.resetAll();
    }

    @Test
    @DisplayName("两个用户同时仿真：启动、暂停、重置互不影响")
    void twoUsersRunIndependently() throws Exception {
        String admin = login("admin", "admin123");
        String dispatcher = login("dispatcher", "dispatcher123");

        long adminExperiment = start(admin, "nearest", 1, 101L);
        long dispatcherExperiment = start(dispatcher, "greedy-eta", 1, 202L);
        assertNotEquals(adminExperiment, dispatcherExperiment, "两个用户应各自生成实验记录");

        mockMvc.perform(get("/api/sim/status").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.paused").value(false));
        mockMvc.perform(get("/api/sim/status").header("Authorization", "Bearer " + dispatcher))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.paused").value(false));

        mockMvc.perform(post("/api/sim/pause").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paused").value(true));
        mockMvc.perform(get("/api/sim/status").header("Authorization", "Bearer " + dispatcher))
                .andExpect(jsonPath("$.data.paused").value(false))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        mockMvc.perform(post("/api/sim/resume").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sim/reset").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IDLE"));
        mockMvc.perform(get("/api/sim/status").header("Authorization", "Bearer " + dispatcher))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        mockMvc.perform(post("/api/sim/reset").header("Authorization", "Bearer " + dispatcher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IDLE"));

        assertEquals("ABORTED", experimentRepository.findById(adminExperiment).orElseThrow().getStatus());
        assertEquals("ABORTED", experimentRepository.findById(dispatcherExperiment).orElseThrow().getStatus());
    }

    private long start(String token, String algorithm, int agvCount, long seed) throws Exception {
        String body = mockMvc.perform(post("/api/sim/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roadnetId":1,"taskSetId":1,"algorithm":"%s","agvCount":%d,"seed":%d}
                                """.formatted(algorithm, agvCount, seed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("experimentId").asLong();
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
