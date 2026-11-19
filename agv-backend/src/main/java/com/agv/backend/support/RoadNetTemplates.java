package com.agv.backend.support;

/**
 * 预置路网模板。前端"套用模板"直接读这些数据；也可由管理员把模板导入后另存为副本。
 * <p>
 * 模板必须满足 RoadNetValidator 的规则：双向边、无死胡同、缓存位在支路末端、充电位可达全部服务节点。
 * 区域 zones 只做渲染，不参与校验与路径搜索。
 */
public final class RoadNetTemplates {

    private RoadNetTemplates() {
    }

    /** 标准车间：双充电位 + 侧边缓存位 + 两个拣货位 + 一个上货位。 */
    public static final String STANDARD_WORKSHOP = """
            {
              "nodes": [
                {"id": "CH1", "x": 2,  "y": 2,  "type": "CHARGER",   "capacity": 4},
                {"id": "CH2", "x": 18, "y": 2,  "type": "CHARGER",   "capacity": 4},
                {"id": "J1",  "x": 2,  "y": 8,  "type": "JUNCTION",  "capacity": 1},
                {"id": "J5",  "x": 6,  "y": 8,  "type": "JUNCTION",  "capacity": 1},
                {"id": "J2",  "x": 10, "y": 8,  "type": "JUNCTION",  "capacity": 1},
                {"id": "J3",  "x": 18, "y": 8,  "type": "JUNCTION",  "capacity": 1},
                {"id": "J4",  "x": 10, "y": 2,  "type": "JUNCTION",  "capacity": 1},
                {"id": "B1",  "x": 6,  "y": 6,  "type": "BUFFER",    "capacity": 1},
                {"id": "P1",  "x": 2,  "y": 16, "type": "PICKING",   "capacity": 1, "serviceTicks": 5},
                {"id": "P2",  "x": 10, "y": 16, "type": "PICKING",   "capacity": 1, "serviceTicks": 5},
                {"id": "L1",  "x": 18, "y": 16, "type": "LOADING",   "capacity": 1, "serviceTicks": 3}
              ],
              "zones": [
                {"id": "Z1", "kind": "CHARGING", "x": 0,  "y": 0,  "width": 22, "height": 4,  "label": "AGV充电区"},
                {"id": "Z2", "kind": "PICKING",  "x": 0,  "y": 14, "width": 14, "height": 4,  "label": "拣货区"},
                {"id": "Z3", "kind": "LOADING",  "x": 14, "y": 14, "width": 8,  "height": 4,  "label": "上货区"}
              ],
              "edges": [
                {"id": "E1",  "from": "CH1", "to": "J1", "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E2",  "from": "J1",  "to": "J5", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E3",  "from": "J5",  "to": "J2", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E4",  "from": "J2",  "to": "J3", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E5",  "from": "J3",  "to": "CH2", "length": 6, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E6",  "from": "CH1", "to": "J4", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E7",  "from": "J4",  "to": "CH2", "length": 8, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E8",  "from": "J4",  "to": "J2", "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E9",  "from": "J1",  "to": "P1", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E10", "from": "J2",  "to": "P2", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E11", "from": "J3",  "to": "L1", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E12", "from": "J5",  "to": "B1", "length": 2,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1}
              ]
            }
            """;

    /** 直线车间：一条主巷道带两个拣货位和一个上货位，适合第一次跑通仿真。 */
    public static final String LINE_WORKSHOP = """
            {
              "nodes": [
                {"id": "CH1", "x": 0,  "y": 0, "type": "CHARGER", "capacity": 4},
                {"id": "CH2", "x": 40, "y": 0, "type": "CHARGER", "capacity": 4},
                {"id": "J1",  "x": 10, "y": 0, "type": "JUNCTION", "capacity": 1},
                {"id": "J2",  "x": 20, "y": 0, "type": "JUNCTION", "capacity": 1},
                {"id": "J3",  "x": 30, "y": 0, "type": "JUNCTION", "capacity": 1},
                {"id": "P1",  "x": 10, "y": 8, "type": "PICKING", "capacity": 1, "serviceTicks": 5},
                {"id": "P2",  "x": 20, "y": 8, "type": "PICKING", "capacity": 1, "serviceTicks": 5},
                {"id": "L1",  "x": 30, "y": 8, "type": "LOADING", "capacity": 1, "serviceTicks": 3}
              ],
              "zones": [
                {"id": "Z1", "kind": "CHARGING", "x": -2, "y": -2, "width": 44, "height": 4, "label": "AGV充电区"},
                {"id": "Z2", "kind": "PICKING",  "x": 8,  "y": 6,  "width": 14, "height": 4, "label": "拣货区"},
                {"id": "Z3", "kind": "LOADING",  "x": 28, "y": 6,  "width": 4,  "height": 4, "label": "上货区"}
              ],
              "edges": [
                {"id": "E1", "from": "CH1", "to": "J1", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E2", "from": "J1",  "to": "J2", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E3", "from": "J2",  "to": "J3", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E4", "from": "J3",  "to": "CH2", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E5", "from": "J1",  "to": "P1", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E6", "from": "J2",  "to": "P2", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E7", "from": "J3",  "to": "L1", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1}
              ]
            }
            """;

    /**
     * 双车道车间：和标准车间同一套拓扑，主环路 E1–E8 为双向双车道，通往服务节点和缓存位的分支保持单车道。
     * 用于演示对向会车、双车道预约和 3D 视觉车道。
     */
    public static final String DUAL_LANE_WORKSHOP = """
            {
              "nodes": [
                {"id": "CH1", "x": 2,  "y": 2,  "type": "CHARGER",   "capacity": 4},
                {"id": "CH2", "x": 18, "y": 2,  "type": "CHARGER",   "capacity": 4},
                {"id": "J1",  "x": 2,  "y": 8,  "type": "JUNCTION",  "capacity": 1},
                {"id": "J5",  "x": 6,  "y": 8,  "type": "JUNCTION",  "capacity": 1},
                {"id": "J2",  "x": 10, "y": 8,  "type": "JUNCTION",  "capacity": 1},
                {"id": "J3",  "x": 18, "y": 8,  "type": "JUNCTION",  "capacity": 1},
                {"id": "J4",  "x": 10, "y": 2,  "type": "JUNCTION",  "capacity": 1},
                {"id": "B1",  "x": 6,  "y": 6,  "type": "BUFFER",    "capacity": 1},
                {"id": "P1",  "x": 2,  "y": 16, "type": "PICKING",   "capacity": 1, "serviceTicks": 5},
                {"id": "P2",  "x": 10, "y": 16, "type": "PICKING",   "capacity": 1, "serviceTicks": 5},
                {"id": "L1",  "x": 18, "y": 16, "type": "LOADING",   "capacity": 1, "serviceTicks": 3}
              ],
              "zones": [
                {"id": "Z1", "kind": "CHARGING", "x": 0,  "y": 0,  "width": 22, "height": 4, "label": "AGV充电区"},
                {"id": "Z2", "kind": "PICKING",  "x": 0,  "y": 14, "width": 14, "height": 4, "label": "拣货区"},
                {"id": "Z3", "kind": "LOADING",  "x": 14, "y": 14, "width": 8,  "height": 4, "label": "上货区"}
              ],
              "edges": [
                {"id": "E1",  "from": "CH1", "to": "J1", "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E2",  "from": "J1",  "to": "J5", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E3",  "from": "J5",  "to": "J2", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E4",  "from": "J2",  "to": "J3", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E5",  "from": "J3",  "to": "CH2", "length": 6, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E6",  "from": "CH1", "to": "J4", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E7",  "from": "J4",  "to": "CH2", "length": 8, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E8",  "from": "J4",  "to": "J2", "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E9",  "from": "J1",  "to": "P1", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E10", "from": "J2",  "to": "P2", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E11", "from": "J3",  "to": "L1", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E12", "from": "J5",  "to": "B1", "length": 2,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"}
              ]
            }
            """;

    /**
     * 仓储车间：左侧拣货区、右侧上货区、中间存储区与货架阵列、左下充电区、右下停放区。
     * 外圈主环为双车道，内部巷道与站点支路为单车道。
     */
    public static final String WAREHOUSE_WORKSHOP = """
            {
              "nodes": [
                {"id": "CH1", "x": 3,  "y": 4,  "type": "CHARGER", "capacity": 4},
                {"id": "CH2", "x": 7,  "y": 4,  "type": "CHARGER", "capacity": 4},
                {"id": "PK1", "x": 53, "y": 4,  "type": "PARKING", "capacity": 4},
                {"id": "PK2", "x": 57, "y": 4,  "type": "PARKING", "capacity": 4},
                {"id": "P1",  "x": 3,  "y": 14, "type": "PICKING", "capacity": 1, "serviceTicks": 5},
                {"id": "P2",  "x": 3,  "y": 24, "type": "PICKING", "capacity": 1, "serviceTicks": 5},
                {"id": "P3",  "x": 7,  "y": 20, "type": "PICKING", "capacity": 1, "serviceTicks": 5},
                {"id": "L1",  "x": 53, "y": 14, "type": "LOADING", "capacity": 1, "serviceTicks": 3},
                {"id": "L2",  "x": 53, "y": 24, "type": "LOADING", "capacity": 1, "serviceTicks": 3},
                {"id": "L3",  "x": 57, "y": 20, "type": "LOADING", "capacity": 1, "serviceTicks": 3},
                {"id": "S1",  "x": 20, "y": 6,  "type": "STORAGE", "capacity": 1, "serviceTicks": 4},
                {"id": "S2",  "x": 40, "y": 6,  "type": "STORAGE", "capacity": 1, "serviceTicks": 4},
                {"id": "J1",  "x": 3,  "y": 10, "type": "JUNCTION", "capacity": 1},
                {"id": "J2",  "x": 20, "y": 10, "type": "JUNCTION", "capacity": 1},
                {"id": "J3",  "x": 40, "y": 10, "type": "JUNCTION", "capacity": 1},
                {"id": "J4",  "x": 53, "y": 10, "type": "JUNCTION", "capacity": 1},
                {"id": "J5",  "x": 3,  "y": 20, "type": "JUNCTION", "capacity": 1},
                {"id": "J6",  "x": 20, "y": 20, "type": "JUNCTION", "capacity": 1},
                {"id": "J7",  "x": 40, "y": 20, "type": "JUNCTION", "capacity": 1},
                {"id": "J8",  "x": 53, "y": 20, "type": "JUNCTION", "capacity": 1},
                {"id": "J9",  "x": 3,  "y": 30, "type": "JUNCTION", "capacity": 1},
                {"id": "J10", "x": 20, "y": 30, "type": "JUNCTION", "capacity": 1},
                {"id": "J11", "x": 40, "y": 30, "type": "JUNCTION", "capacity": 1},
                {"id": "J12", "x": 53, "y": 30, "type": "JUNCTION", "capacity": 1},
                {"id": "B1",  "x": 8,  "y": 34, "type": "BUFFER", "capacity": 1},
                {"id": "B2",  "x": 48, "y": 34, "type": "BUFFER", "capacity": 1}
              ],
              "zones": [
                {"id": "Z1", "kind": "CHARGING", "x": 1,  "y": 2,  "width": 9,  "height": 5,  "label": "AGV充电区"},
                {"id": "Z2", "kind": "PARKING",  "x": 51, "y": 2,  "width": 9,  "height": 5,  "label": "AGV停放区"},
                {"id": "Z3", "kind": "PICKING",  "x": 1,  "y": 12, "width": 9,  "height": 18, "label": "拣货区"},
                {"id": "Z4", "kind": "LOADING",  "x": 51, "y": 12, "width": 9,  "height": 18, "label": "上货区"},
                {"id": "Z5", "kind": "STORAGE",  "x": 14, "y": 1,  "width": 32, "height": 38, "label": "存储区"}
              ],
              "edges": [
                {"id": "E1",  "from": "CH1", "to": "J1", "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E2",  "from": "CH2", "to": "CH1", "length": 4, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E3",  "from": "PK1", "to": "J4", "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E4",  "from": "PK2", "to": "PK1", "length": 4, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E5",  "from": "P1",  "to": "J1", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E6",  "from": "P2",  "to": "J5", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E7",  "from": "P3",  "to": "J5", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E8",  "from": "L1",  "to": "J4", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E9",  "from": "L2",  "to": "J8", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E10", "from": "L3",  "to": "J8", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E11", "from": "S1",  "to": "J2", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E12", "from": "S2",  "to": "J3", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E13", "from": "J1",  "to": "J2", "length": 17, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E14", "from": "J2",  "to": "J3", "length": 20, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E15", "from": "J3",  "to": "J4", "length": 13, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E16", "from": "J5",  "to": "J6", "length": 17, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E17", "from": "J6",  "to": "J7", "length": 20, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E18", "from": "J7",  "to": "J8", "length": 13, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E19", "from": "J9",  "to": "J10", "length": 17, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E20", "from": "J10", "to": "J11", "length": 20, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E21", "from": "J11", "to": "J12", "length": 13, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E22", "from": "J1",  "to": "J5", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E23", "from": "J5",  "to": "J9", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E24", "from": "J2",  "to": "J6", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E25", "from": "J6",  "to": "J10", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E26", "from": "J3",  "to": "J7", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E27", "from": "J7",  "to": "J11", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E28", "from": "J4",  "to": "J8", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E29", "from": "J8",  "to": "J12", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "DUAL"},
                {"id": "E30", "from": "B1",  "to": "J9",  "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"},
                {"id": "E31", "from": "B2",  "to": "J12", "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1, "laneMode": "SINGLE"}
              ]
            }
            """;
}
