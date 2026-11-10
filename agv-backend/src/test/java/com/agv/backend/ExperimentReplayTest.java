package com.agv.backend;

import com.agv.backend.entity.Experiment;
import com.agv.backend.entity.SysUser;
import com.agv.backend.repository.ExperimentMetricRepository;
import com.agv.backend.repository.ExperimentRepository;
import com.agv.backend.repository.SysUserRepository;
import com.agv.backend.service.SimulationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExperimentReplayTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentMetricRepository metricRepository;

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SimulationService simulationService;

    @BeforeEach
    void resetState() {
        simulationService.reset();
        metricRepository.deleteAll();
        experimentRepository.deleteAll();
    }

    @Test
    @DisplayName("完成实验落输入快照，回放同 seed 指标一致")
    void finishedExperimentReplaysConsistently() throws Exception {
        String token = login("admin", "admin123");
        long experimentId = runExperiment(token);

        Experiment saved = experimentRepository.findById(experimentId).orElseThrow();
        assertNotNull(saved.getRoadnetSnapshot(), "实验应保存路网快照");
        assertNotNull(saved.getTasksetSnapshot(), "实验应保存任务集快照");

        mockMvc.perform(post("/api/experiment/" + experimentId + "/replay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.experimentId").value(experimentId))
                .andExpect(jsonPath("$.data.consistent").value(true))
                .andExpect(jsonPath("$.data.replayId").isNumber())
                .andExpect(jsonPath("$.data.original.makespan").isNumber())
                .andExpect(jsonPath("$.data.replay.makespan").isNumber());

        org.junit.jupiter.api.Assertions.assertEquals(2, experimentRepository.count(),
                "回放应新增一条实验记录，原记录不变");
    }

    @Test
    @DisplayName("旧实验没有快照：回放返回 4005 / 400")
    void experimentWithoutSnapshotIsRejected() throws Exception {
        Experiment legacy = experiment("admin", null, null);
        experimentRepository.save(legacy);
        String token = login("admin", "admin123");

        mockMvc.perform(post("/api/experiment/" + legacy.getId() + "/replay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4005));
    }

    @Test
    @DisplayName("实验没有指标记录：回放返回 4006 / 400")
    void experimentWithoutMetricIsRejected() throws Exception {
        Experiment noMetric = experiment("admin", "{}", "{}");
        experimentRepository.save(noMetric);
        String token = login("admin", "admin123");

        mockMvc.perform(post("/api/experiment/" + noMetric.getId() + "/replay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4006));
    }

    @Test
    @DisplayName("非创建人回放：4030 / 403")
    void otherUserCannotReplay() throws Exception {
        long experimentId = runExperiment(login("admin", "admin123"));
        String dispatcher = login("dispatcher", "dispatcher123");

        mockMvc.perform(post("/api/experiment/" + experimentId + "/replay")
                        .header("Authorization", "Bearer " + dispatcher))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));
    }

    private long runExperiment(String token) throws Exception {
        String body = mockMvc.perform(post("/api/experiment/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roadnetId":1,"taskSetId":1,"algorithm":"nearest","agvCount":3,"seed":20261107}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("FINISHED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path(0).path("id").asLong();
    }

    private Experiment experiment(String username, String roadnetSnapshot, String tasksetSnapshot) {
        SysUser user = userRepository.findByUsername(username).orElseThrow();
        Experiment experiment = new Experiment();
        experiment.setRoadnetId(1L);
        experiment.setTaskSetId(1L);
        experiment.setAlgorithm("nearest");
        experiment.setAgvCount(3);
        experiment.setSeed(20261107L);
        experiment.setStatus("FINISHED");
        experiment.setCreateUser(user.getId());
        experiment.setRoadnetSnapshot(roadnetSnapshot);
        experiment.setTasksetSnapshot(tasksetSnapshot);
        return experiment;
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
