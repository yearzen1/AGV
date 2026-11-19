package com.agv.backend.support;

import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.path.AStarPlanner;
import com.agv.algorithm.sim.TaskOperation;
import com.agv.backend.common.BusinessException;
import com.agv.backend.dto.TaskDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务集与绑定路网的一致性校验。
 * <p>
 * 节点编号只在单张路网内唯一，因此任务集必须绑定路网；任务端点只能是服务节点，
 * 作业类型决定起点/终点允许的节点类型，且起终点之间必须存在有向路径。
 * 错误信息带任务序号与节点编号，供前端定位到行。
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
        boolean hasService = net.nodes().values().stream().anyMatch(NetNode::isServiceNode);
        if (!hasService) {
            errors.add("该路网没有拣货位、上货位或存储位，无法创建任务集");
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
            if (from.id().equals(to.id())) {
                errors.add(label + "：起点与终点不能相同");
                continue;
            }
            TaskOperation operation = task.operation() == null ? TaskOperation.LOAD : task.operation();
            if (!allowedFrom(from.type(), operation)) {
                errors.add(label + "：" + operation + " 任务的起点 " + task.from()
                        + " 必须是" + expectedFrom(operation));
                continue;
            }
            if (!allowedTo(to.type(), operation)) {
                errors.add(label + "：" + operation + " 任务的终点 " + task.to()
                        + " 必须是" + expectedTo(operation));
                continue;
            }
            if (!planner.plan(net, from.id(), to.id()).found()) {
                errors.add(label + "：" + from.id() + " → " + to.id() + " 不可达");
            }
        }
        return errors;
    }

    private boolean allowedFrom(NodeType type, TaskOperation operation) {
        if (type == NodeType.STORAGE) {
            return true;
        }
        return operation == TaskOperation.LOAD ? type == NodeType.PICKING : type == NodeType.LOADING;
    }

    private boolean allowedTo(NodeType type, TaskOperation operation) {
        if (type == NodeType.STORAGE) {
            return true;
        }
        return operation == TaskOperation.LOAD ? type == NodeType.LOADING : type == NodeType.PICKING;
    }

    private String expectedFrom(TaskOperation operation) {
        return operation == TaskOperation.LOAD ? "拣货位或存储位" : "上货位或存储位";
    }

    private String expectedTo(TaskOperation operation) {
        return operation == TaskOperation.LOAD ? "上货位或存储位" : "拣货位或存储位";
    }

    public void validateOrThrow(RoadNet net, List<TaskDto> tasks) {
        List<String> errors = validate(net, tasks);
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join("；", errors));
        }
    }
}
