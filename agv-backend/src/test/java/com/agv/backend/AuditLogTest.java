package com.agv.backend;

import com.agv.backend.repository.AuditLogRepository;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void resetAudit() {
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("登录失败写 FAILURE 审计，管理员可查")
    void loginFailureIsAudited() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + token)
                        .param("action", "LOGIN")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].result").value("FAILURE"))
                .andExpect(jsonPath("$.data.records[0].username").value("admin"))
                .andExpect(jsonPath("$.data.records[1].result").value("SUCCESS"));
    }

    @Test
    @DisplayName("保存路网写 ROADNET_UPDATE 审计，带目标 ID")
    void roadnetUpdateIsAudited() throws Exception {
        String token = login("admin", "admin123");
        String detail = mockMvc.perform(get("/api/roadnet/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(detail).path("data");
        String body = objectMapper.writeValueAsString(
                Map.of("name", "标准车间（双充电位）", "roadnet", data));

        mockMvc.perform(put("/api/roadnet/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + token)
                        .param("action", "ROADNET_UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].targetType").value("ROADNET"))
                .andExpect(jsonPath("$.data.records[0].targetId").value("1"))
                .andExpect(jsonPath("$.data.records[0].username").value("admin"))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCESS"));
    }

    @Test
    @DisplayName("调度人员读审计日志：4030 / 403")
    void dispatcherCannotReadAudit() throws Exception {
        String token = login("dispatcher", "dispatcher123");

        mockMvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));
    }

    @Test
    @DisplayName("审计分页参数非法：4000 / 400")
    void invalidPagingRejected() throws Exception {
        String token = login("admin", "admin123");

        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000));
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
