package com.agv.backend.support;

import com.agv.backend.entity.SysDict;
import com.agv.backend.entity.SysUser;
import com.agv.backend.entity.WarehouseMap;
import com.agv.backend.repository.SysDictRepository;
import com.agv.backend.repository.SysUserRepository;
import com.agv.backend.repository.WarehouseMapRepository;
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
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserRepository userRepository, SysDictRepository dictRepository,
                           WarehouseMapRepository mapRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.dictRepository = dictRepository;
        this.mapRepository = mapRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedDicts();
        seedTemplates();
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
        if (mapRepository.count() > 0) {
            return;
        }
        mapRepository.save(template("标准车间（双充电位）", RoadNetTemplates.STANDARD_WORKSHOP));
        mapRepository.save(template("直线车间（快速上手）", RoadNetTemplates.LINE_WORKSHOP));
    }

    private WarehouseMap template(String name, String json) {
        WarehouseMap map = new WarehouseMap();
        map.setName(name);
        map.setRoadnetJson(json);
        map.setIsTemplate(true);
        map.setCreateUser(null);
        return map;
    }
}
