package com.agv.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("登录拿到 JWT 后可读路网列表，且预置模板已初始化")
    void loginThenListTemplates() throws Exception {
        String token = login("admin", "admin123");

        mockMvc.perform(get("/api/roadnet/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].isTemplate").value(true));
    }

    @Test
    @DisplayName("未带 token 访问业务接口：401")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/roadnet/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("密码错误：返回业务错误码 4012")
    void wrongPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"bad-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4012));
    }

    @Test
    @DisplayName("没有充电位的路网：后端硬拦截并返回逐条错误")
    void invalidRoadNetIsRejected() throws Exception {
        String token = login("dispatcher", "dispatcher123");

        mockMvc.perform(post("/api/roadnet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "坏路网",
                                  "roadnet": {
                                    "nodes": [{"id":"W1","x":0,"y":0,"type":"WORKSTATION"}],
                                    "edges": []
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.data[0].code").value("NO_CHARGER"));
    }

    @Test
    @DisplayName("调度人员访问用户管理：403")
    void dispatcherCannotManageUsers() throws Exception {
        String token = login("dispatcher", "dispatcher123");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("模板路网可通过校验端点返回空错误列表")
    void validateEndpointAcceptsTemplate() throws Exception {
        String token = login("admin", "admin123");
        String response = mockMvc.perform(get("/api/roadnet/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 校验端点接收的是裸 RoadNetPayload，需要把统一响应体里的 data 取出来再回传
        JsonNode data = objectMapper.readTree(response).path("data");
        String roadnetJson = objectMapper.writeValueAsString(data);

        mockMvc.perform(post("/api/roadnet/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roadnetJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("任务集已初始化，且能跑通双算法对比实验并入库")
    void taskSetAndComparisonExperiment() throws Exception {
        String token = login("dispatcher", "dispatcher123");

        mockMvc.perform(get("/api/taskset/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].taskCount").value(20));

        mockMvc.perform(post("/api/experiment/compare")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mapId":1,"taskSetId":1,"agvCount":3,"seed":20261107}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("FINISHED"))
                .andExpect(jsonPath("$.data[1].status").value("FINISHED"))
                .andExpect(jsonPath("$.data[0].makespan").isNumber())
                .andExpect(jsonPath("$.data[1].conflictCount").isNumber());

        mockMvc.perform(get("/api/experiment/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = objectMapper.readTree(body);
        String token = root.path("data").path("token").asText();
        assertFalse(token.isBlank(), "登录未返回 token");
        return token;
    }
}
