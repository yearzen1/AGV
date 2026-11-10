package com.agv.backend.support;

import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.path.AStarPlanner;
import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.TaskDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务集与绑定路网的一致性校验。
 * <p>
 * 节点编号只在单张路网内唯一，因此任务集必须绑定路网；任务端点只能是工位，
 * 且起终点之间必须存在有向路径。错误信息带任务序号与节点编号，供前端定位到行。
 */
@Component
public class TaskSetValidator {

    private final AStarPlanner planner = AStarPlanner.defaultPlanner();

    public List<String> validate(RoadNet net, List<TaskDto> tasks) {
        List<String> errors = new ArrayList<>();
        if (tasks == null || tasks.isEmpty()) {
            errors.add("任务集至少需要一条任务");
            return errors;
        }
        boolean hasWorkstation = net.nodes().values().stream()
                .anyMatch(node -> node.type() == NodeType.WORKSTATION);
        if (!hasWorkstation) {
            errors.add("该路网没有工位，无法创建任务集");
            return errors;
        }
        for (TaskDto task : tasks) {
            String label = "任务 " + (task.id() == null ? "?" : task.id());
            NetNode from = net.node(task.from());
            NetNode to = net.node(task.to());
            if (from == null) {
                errors.add(label + "：起点 " + task.from() + " 不在该路网中");
                continue;
            }
            if (to == null) {
                errors.add(label + "：终点 " + task.to() + " 不在该路网中");
                continue;
            }
            if (from.type() != NodeType.WORKSTATION) {
                errors.add(label + "：起点 " + task.from() + " 不是工位");
                continue;
            }
            if (to.type() != NodeType.WORKSTATION) {
                errors.add(label + "：终点 " + task.to() + " 不是工位");
                continue;
            }
            if (from.id().equals(to.id())) {
                errors.add(label + "：起点与终点不能相同");
                continue;
            }
            if (!planner.plan(net, from.id(), to.id()).found()) {
                errors.add(label + "：" + from.id() + " → " + to.id() + " 不可达");
            }
        }
        return errors;
    }

    public void validateOrThrow(RoadNet net, List<TaskDto> tasks) {
        List<String> errors = validate(net, tasks);
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join("；", errors));
        }
    }
}
