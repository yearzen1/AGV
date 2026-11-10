package com.agv.backend;

import com.agv.backend.dto.TaskDto;
import com.agv.backend.dto.TaskSetPayload;
import com.agv.backend.entity.TaskSet;
import com.agv.backend.repository.TaskSetRepository;
import com.agv.backend.support.DataInitializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务集与路网绑定后的校验：节点存在、类型、起终点不同、可达、跨图启动与删除保护。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskSetValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskSetRepository taskSetRepository;

    @Autowired
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("任务集绑定路网后可创建，并返回所属路网名称")
    void createsTaskSetBoundToMap() throws Exception {
        String token = login("dispatcher", "dispatcher123");

        mockMvc.perform(post("/api/taskset")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskSetBody("绑定测试", 1, "W1", "W2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roadnetId").value(1))
                .andExpect(jsonPath("$.data.roadnetName").value("标准车间（双充电位）"))
                .andExpect(jsonPath("$.data.taskCount").value(1));
    }

    @Test
    @DisplayName("缺少 roadnetId：拒绝创建")
    void rejectsTaskSetWithoutMapId() throws Exception {
        String token = login("dispatcher", "dispatcher123");

        mockMvc.perform(post("/api/taskset")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"缺地图","taskSet":{"tasks":[{"id":1,"from":"W1","to":"W2"}]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000));
    }

    @Test
    @DisplayName("起点不在路网中：错误信息带任务序号与节点编号")
    void rejectsUnknownNode() throws Exception {
        String token = login("dispatcher", "dispatcher123");

        mockMvc.perform(post("/api/taskset")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskSetBody("未知节点", 1, "W9", "W2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.message").value("任务 1：起点 W9 不在该路网中"));
    }

    @Test
    @DisplayName("充电位不能作为任务端点")
    void rejectsNonWorkstationEndpoint() throws Exception {
        String token = login("dispatcher", "dispatcher123");

        mockMvc.perform(post("/api/taskset")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskSetBody("充电位端点", 1, "CH1", "W2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.message").value("任务 1：起点 CH1 不是工位"));
    }

    @Test
    @DisplayName("起点与终点相同：拒绝创建")
    void rejectsSameEndpoints() throws Exception {
        String token = login("dispatcher", "dispatcher123");

        mockMvc.perform(post("/api/taskset")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskSetBody("同点", 1, "W1", "W1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.message").value("任务 1：起点与终点不能相同"));
    }

    @Test
    @DisplayName("单向环上反向不可达：拒绝创建")
    void rejectsUnreachableEndpoints() throws Exception {
        String token = login("dispatcher", "dispatcher123");
        long roadnetId = createOneWayBranchMap(token);

        mockMvc.perform(post("/api/taskset")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskSetBody("不可达", roadnetId, "W2", "W1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.message").value("任务 1：W2 → W1 不可达"));
    }

    @Test
    @DisplayName("仿真启动时任务集与路网不匹配：拒绝启动")
    void rejectsSimulationWithMismatchedMap() throws Exception {
        String token = login("dispatcher", "dispatcher123");
        long taskSetId = createTaskSet(token, "跨图启动", 1, "W1", "W2");

        mockMvc.perform(post("/api/sim/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roadnetId":2,"taskSetId":%d,"algorithm":"nearest","agvCount":3,"seed":20261107}
                                """.formatted(taskSetId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.message").value(
                        "任务集「跨图启动」绑定的路网与所选路网不一致"));
    }

    @Test
    @DisplayName("删除仍被任务集引用的路网：拒绝删除")
    void rejectsDeletingReferencedMap() throws Exception {
        String token = login("dispatcher", "dispatcher123");
        long roadnetId = createOneWayBranchMap(token);
        createTaskSet(token, "引用路网", roadnetId, "W1", "W2");

        mockMvc.perform(delete("/api/roadnet/" + roadnetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.message").value(
                        "该路网被 1 个任务集使用，请先删除或改绑"));
    }

    @Test
    @DisplayName("启动时把未绑定路网的旧任务集绑到标准车间")
    void bindsOrphanTaskSetsToStandardWorkshop() throws Exception {
        TaskSet orphan = new TaskSet();
        orphan.setName("旧任务集");
        orphan.setTasksJson(objectMapper.writeValueAsString(
                new TaskSetPayload(List.of(new TaskDto(1, "W1", "W2")))));
        taskSetRepository.save(orphan);

        dataInitializer.run();

        String token = login("dispatcher", "dispatcher123");
        mockMvc.perform(get("/api/taskset/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '旧任务集')].roadnetId").value(1));
    }

    private String taskSetBody(String name, long roadnetId, String from, String to) {
        return """
                {
                  "name": "%s",
                  "roadnetId": %d,
                  "taskSet": {"tasks": [{"id": 1, "from": "%s", "to": "%s"}]}
                }
                """.formatted(name, roadnetId, from, to);
    }

    private long createTaskSet(String token, String name, long roadnetId, String from, String to)
            throws Exception {
        String response = mockMvc.perform(post("/api/taskset")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskSetBody(name, roadnetId, from, to)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    /**
     * 合法但不对称的单向路网：CH1 能到达 W1 和 W2，W1 可经 J1 回到 CH1 再去 W2，
     * 但 W2 只能在 W2 ↔ J2 之间循环，无法到达 W1。
     */
    private long createOneWayBranchMap(String token) throws Exception {
        String response = mockMvc.perform(post("/api/roadnet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "单向分支",
                                  "roadnet": {
                                    "nodes": [
                                      {"id":"CH1","x":0,"y":0,"type":"CHARGER","capacity":4},
                                      {"id":"W1","x":10,"y":0,"type":"WORKSTATION","capacity":1},
                                      {"id":"W2","x":20,"y":0,"type":"WORKSTATION","capacity":1},
                                      {"id":"J1","x":10,"y":10,"type":"JUNCTION","capacity":1},
                                      {"id":"J2","x":20,"y":10,"type":"JUNCTION","capacity":1},
                                      {"id":"J3","x":30,"y":10,"type":"JUNCTION","capacity":1}
                                    ],
                                    "edges": [
                                      {"id":"E1","from":"CH1","to":"W1","length":10,"direction":"ONE_WAY","speed":1.0,"capacity":1},
                                      {"id":"E2","from":"W1","to":"J1","length":10,"direction":"ONE_WAY","speed":1.0,"capacity":1},
                                      {"id":"E3","from":"J1","to":"CH1","length":10,"direction":"ONE_WAY","speed":1.0,"capacity":1},
                                      {"id":"E4","from":"CH1","to":"W2","length":20,"direction":"ONE_WAY","speed":1.0,"capacity":1},
                                      {"id":"E5","from":"W2","to":"J2","length":10,"direction":"ONE_WAY","speed":1.0,"capacity":1},
                                      {"id":"E6","from":"J2","to":"J3","length":10,"direction":"ONE_WAY","speed":1.0,"capacity":1},
                                      {"id":"E7","from":"J3","to":"W2","length":10,"direction":"ONE_WAY","speed":1.0,"capacity":1}
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = objectMapper.readTree(response).path("data").path("id").asLong();
        assertTrue(id > 0, "创建路网未返回 id");
        return id;
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(body);
        return root.path("data").path("token").asText();
    }
}
