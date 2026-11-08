package com.agv.backend.support;

/**
 * 预置路网模板。前端"套用模板"直接读这些数据；也可由管理员把模板导入后另存为副本。
 * <p>
 * 模板必须满足 RoadNetValidator 的 8 条规则：双向边、无死胡同、缓存位在支路末端、充电位可达全部工位。
 */
public final class RoadNetTemplates {

    private RoadNetTemplates() {
    }

    /** 标准车间：双充电位 + 侧边缓存位 + 三条巷道。 */
    public static final String STANDARD_WORKSHOP = """
            {
              "nodes": [
                {"id": "CH1", "x": 2,  "y": 2,  "type": "CHARGER"},
                {"id": "CH2", "x": 18, "y": 2,  "type": "CHARGER"},
                {"id": "J1",  "x": 2,  "y": 8,  "type": "JUNCTION"},
                {"id": "J5",  "x": 6,  "y": 8,  "type": "JUNCTION"},
                {"id": "J2",  "x": 10, "y": 8,  "type": "JUNCTION"},
                {"id": "J3",  "x": 18, "y": 8,  "type": "JUNCTION"},
                {"id": "J4",  "x": 10, "y": 2,  "type": "JUNCTION"},
                {"id": "B1",  "x": 6,  "y": 6,  "type": "BUFFER"},
                {"id": "W1",  "x": 2,  "y": 16, "type": "WORKSTATION"},
                {"id": "W2",  "x": 10, "y": 16, "type": "WORKSTATION"},
                {"id": "W3",  "x": 18, "y": 16, "type": "WORKSTATION"}
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
                {"id": "E9",  "from": "J1",  "to": "W1", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E10", "from": "J2",  "to": "W2", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E11", "from": "J3",  "to": "W3", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E12", "from": "J5",  "to": "B1", "length": 2,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1}
              ]
            }
            """;

    /** 直线车间：一条主巷道带三个工位，适合第一次跑通仿真。 */
    public static final String LINE_WORKSHOP = """
            {
              "nodes": [
                {"id": "CH1", "x": 0,  "y": 0, "type": "CHARGER"},
                {"id": "CH2", "x": 40, "y": 0, "type": "CHARGER"},
                {"id": "J1",  "x": 10, "y": 0, "type": "JUNCTION"},
                {"id": "J2",  "x": 20, "y": 0, "type": "JUNCTION"},
                {"id": "J3",  "x": 30, "y": 0, "type": "JUNCTION"},
                {"id": "W1",  "x": 10, "y": 8, "type": "WORKSTATION"},
                {"id": "W2",  "x": 20, "y": 8, "type": "WORKSTATION"},
                {"id": "W3",  "x": 30, "y": 8, "type": "WORKSTATION"}
              ],
              "edges": [
                {"id": "E1", "from": "CH1", "to": "J1", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E2", "from": "J1",  "to": "J2", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E3", "from": "J2",  "to": "J3", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E4", "from": "J3",  "to": "CH2", "length": 10, "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E5", "from": "J1",  "to": "W1", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E6", "from": "J2",  "to": "W2", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E7", "from": "J3",  "to": "W3", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1}
              ]
            }
            """;
}
