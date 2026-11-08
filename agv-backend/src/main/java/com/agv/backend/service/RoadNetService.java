package com.agv.backend.service;

import com.agv.algorithm.model.EdgeDirection;
import com.agv.algorithm.model.LaneMode;
import com.agv.algorithm.model.NetEdge;
import com.agv.algorithm.model.NetNode;
import com.agv.algorithm.model.NodeType;
import com.agv.algorithm.model.RoadNet;
import com.agv.algorithm.model.RoadNetValidator;
import com.agv.algorithm.model.ValidationResult;
import com.agv.backend.common.BusinessException;
import com.agv.backend.common.RoadNetInvalidException;
import com.agv.backend.dto.EdgeDto;
import com.agv.backend.dto.NodeDto;
import com.agv.backend.dto.RoadNetPayload;
import com.agv.backend.dto.RoadNetSummary;
import com.agv.backend.dto.SaveRoadNetRequest;
import com.agv.backend.entity.SysUser;
import com.agv.backend.entity.WarehouseMap;
import com.agv.backend.repository.WarehouseMapRepository;
import com.agv.backend.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 路网管理。整张图存为一个 roadnet_json；保存前走后端硬校验。
 */
@Service
public class RoadNetService {

    private final WarehouseMapRepository mapRepository;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;

    public RoadNetService(WarehouseMapRepository mapRepository, ObjectMapper objectMapper, CurrentUser currentUser) {
        this.mapRepository = mapRepository;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<RoadNetSummary> list() {
        List<RoadNetSummary> result = new ArrayList<>();
        for (WarehouseMap map : mapRepository.findAllByOrderByIdAsc()) {
            RoadNetPayload payload = readPayload(map.getRoadnetJson());
            result.add(new RoadNetSummary(map.getId(), map.getName(), map.getIsTemplate(),
                    map.getCreateUser(), map.getCreateTime(),
                    payload.nodes().size(), payload.edges().size()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public RoadNetPayload detail(Long id) {
        return readPayload(requireMap(id).getRoadnetJson());
    }

    @Transactional
    public RoadNetSummary create(SaveRoadNetRequest request) {
        validateOrThrow(request.roadnet());
        SysUser user = currentUser.get();
        WarehouseMap map = new WarehouseMap();
        map.setName(request.name());
        map.setRoadnetJson(writePayload(request.roadnet()));
        map.setIsTemplate(Boolean.TRUE.equals(request.isTemplate()));
        map.setCreateUser(user.getId());
        WarehouseMap saved = mapRepository.save(map);
        return new RoadNetSummary(saved.getId(), saved.getName(), saved.getIsTemplate(),
                saved.getCreateUser(), saved.getCreateTime(),
                request.roadnet().nodes().size(), request.roadnet().edges().size());
    }

    @Transactional
    public RoadNetSummary update(Long id, SaveRoadNetRequest request) {
        WarehouseMap map = requireMap(id);
        checkWritable(map);
        validateOrThrow(request.roadnet());
        map.setName(request.name());
        map.setRoadnetJson(writePayload(request.roadnet()));
        if (request.isTemplate() != null) {
            map.setIsTemplate(request.isTemplate());
        }
        WarehouseMap saved = mapRepository.save(map);
        return new RoadNetSummary(saved.getId(), saved.getName(), saved.getIsTemplate(),
                saved.getCreateUser(), saved.getCreateTime(),
                request.roadnet().nodes().size(), request.roadnet().edges().size());
    }

    @Transactional
    public void delete(Long id) {
        WarehouseMap map = requireMap(id);
        checkWritable(map);
        mapRepository.delete(map);
    }

    /** 导出：原样返回，前端可直接下载为 .json。 */
    @Transactional(readOnly = true)
    public RoadNetPayload export(Long id) {
        return readPayload(requireMap(id).getRoadnetJson());
    }

    /**
     * 只做校验不落库，供编辑器边改边验。
     */
    public ValidationResult validate(RoadNetPayload payload) {
        return RoadNetValidator.validate(toNodes(payload), toEdges(payload));
    }

    public RoadNet toModel(RoadNetPayload payload) {
        return RoadNet.of(toNodes(payload), toEdges(payload));
    }

    private void validateOrThrow(RoadNetPayload payload) {
        ValidationResult result = validate(payload);
        if (!result.valid()) {
            throw new RoadNetInvalidException(result.errors());
        }
    }

    private List<NetNode> toNodes(RoadNetPayload payload) {
        List<NetNode> nodes = new ArrayList<>();
        for (NodeDto dto : payload.nodes()) {
            if (dto.id() == null || dto.id().isBlank()) {
                throw new BusinessException("节点编号不能为空");
            }
            nodes.add(new NetNode(dto.id(), dto.x(), dto.y(), parseNodeType(dto.type(), dto.id())));
        }
        return nodes;
    }

    private List<NetEdge> toEdges(RoadNetPayload payload) {
        List<NetEdge> edges = new ArrayList<>();
        for (EdgeDto dto : payload.edges()) {
            if (dto.id() == null || dto.id().isBlank()) {
                throw new BusinessException("路段编号不能为空");
            }
            edges.add(new NetEdge(dto.id(), dto.from(), dto.to(), dto.length(),
                    parseDirection(dto.direction(), dto.id()), dto.speed(), dto.capacity(),
                    parseLaneMode(dto.laneMode())));
        }
        return edges;
    }

    private NodeType parseNodeType(String type, String nodeId) {
        try {
            return NodeType.valueOf(type);
        } catch (RuntimeException ex) {
            throw new BusinessException("节点 " + nodeId + " 的类型非法：" + type);
        }
    }

    private EdgeDirection parseDirection(String direction, String edgeId) {
        try {
            return EdgeDirection.valueOf(direction);
        } catch (RuntimeException ex) {
            throw new BusinessException("路段 " + edgeId + " 的方向非法：" + direction);
        }
    }

    private LaneMode parseLaneMode(String laneMode) {
        return "DUAL".equalsIgnoreCase(laneMode) ? LaneMode.DUAL : LaneMode.SINGLE;
    }

    private WarehouseMap requireMap(Long id) {
        return mapRepository.findById(id)
                .orElseThrow(() -> new BusinessException(4040, "路网不存在：" + id));
    }

    /** 预置模板默认只读，管理员除外。 */
    private void checkWritable(WarehouseMap map) {
        if (Boolean.TRUE.equals(map.getIsTemplate())
                && !SysUser.ROLE_ADMIN.equals(currentUser.get().getRole())) {
            throw new BusinessException(4030, "预置模板不可直接修改，请另存为副本");
        }
    }

    private RoadNetPayload readPayload(String json) {
        try {
            return objectMapper.readValue(json, RoadNetPayload.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(4002, "路网数据损坏，无法解析：" + ex.getOriginalMessage());
        }
    }

    private String writePayload(RoadNetPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(4003, "路网数据序列化失败");
        }
    }
}
