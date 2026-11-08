package com.agv.backend.support;

import com.agv.backend.dto.TaskDto;
import com.agv.backend.dto.TaskSetPayload;
import com.agv.backend.entity.SysDict;
import com.agv.backend.entity.SysUser;
import com.agv.backend.entity.TaskSet;
import com.agv.backend.entity.WarehouseMap;
import com.agv.backend.repository.SysDictRepository;
import com.agv.backend.repository.SysUserRepository;
import com.agv.backend.repository.TaskSetRepository;
import com.agv.backend.repository.WarehouseMapRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化演示数据：两个角色账号、两本字典、两套路网模板。幂等，重启不会重复插入。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysUserRepository userRepository;
    private final SysDictRepository dictRepository;
    private final WarehouseMapRepository mapRepository;
    private final TaskSetRepository taskSetRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public DataInitializer(SysUserRepository userRepository, SysDictRepository dictRepository,
                           WarehouseMapRepository mapRepository, TaskSetRepository taskSetRepository,
                           PasswordEncoder passwordEncoder, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.dictRepository = dictRepository;
        this.mapRepository = mapRepository;
        this.taskSetRepository = taskSetRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedDicts();
        seedTemplates();
        seedTaskSet();
    }

    private void seedUsers() {
        createUserIfAbsent("admin", "admin123", "系统管理员", SysUser.ROLE_ADMIN);
        createUserIfAbsent("dispatcher", "dispatcher123", "调度人员", SysUser.ROLE_DISPATCHER);
    }

    private void createUserIfAbsent(String username, String rawPassword, String realName, String role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRealName(realName);
        user.setRole(role);
        user.setStatus(1);
        userRepository.save(user);
        log.info("初始化账号 {} / {}（角色 {}），首次登录后请修改密码", username, rawPassword, role);
    }

    private void seedDicts() {
        createDictIfAbsent("algorithm", "nearest", "就近分配", 1);
        createDictIfAbsent("algorithm", "greedy-eta", "贪心 ETA", 2);
        createDictIfAbsent("metric", "makespan", "完成时间", 1);
        createDictIfAbsent("metric", "total-path-len", "总路径长", 2);
        createDictIfAbsent("metric", "total-wait-tick", "总等待时间", 3);
        createDictIfAbsent("metric", "conflict-count", "冲突次数", 4);
    }

    private void createDictIfAbsent(String type, String code, String value, int sort) {
        if (dictRepository.existsByDictTypeAndDictCode(type, code)) {
            return;
        }
        dictRepository.save(new SysDict(type, code, value, sort));
    }

    private void seedTemplates() {
        upsertTemplate("标准车间（双充电位）", RoadNetTemplates.STANDARD_WORKSHOP);
        upsertTemplate("直线车间（快速上手）", RoadNetTemplates.LINE_WORKSHOP);
    }

    /** 模板按名称幂等更新：已有库也会同步到最新的标准布局。 */
    private void upsertTemplate(String name, String json) {
        for (WarehouseMap map : mapRepository.findAllByOrderByIdAsc()) {
            if (name.equals(map.getName()) && Boolean.TRUE.equals(map.getIsTemplate())) {
                if (!json.equals(map.getRoadnetJson())) {
                    map.setRoadnetJson(json);
                    mapRepository.save(map);
                    log.info("更新预置模板 {}", name);
                }
                return;
            }
        }
        mapRepository.save(template(name, json));
        log.info("初始化预置模板 {}", name);
    }

    private WarehouseMap template(String name, String json) {
        WarehouseMap map = new WarehouseMap();
        map.setName(name);
        map.setRoadnetJson(json);
        map.setIsTemplate(true);
        map.setCreateUser(null);
        return map;
    }

    /** 演示任务集：20 单在三个工位之间循环，覆盖空驶、会车与等待冲突。 */
    private void seedTaskSet() {
        if (taskSetRepository.count() > 0) {
            return;
        }
        String[] stations = {"W1", "W2", "W3"};
        java.util.List<TaskDto> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String from = stations[i % 3];
            String to = stations[(i + 1 + i / 3) % 3];
            if (from.equals(to)) {
                to = stations[(i + 2) % 3];
            }
            tasks.add(new TaskDto(i + 1, from, to));
        }
        try {
            TaskSet set = new TaskSet();
            set.setName("演示任务集（20 单）");
            set.setTasksJson(objectMapper.writeValueAsString(new TaskSetPayload(tasks)));
            set.setCreateUser(null);
            taskSetRepository.save(set);
            log.info("初始化演示任务集：20 单");
        } catch (JsonProcessingException ex) {
            log.warn("演示任务集初始化失败", ex);
        }
    }
}
