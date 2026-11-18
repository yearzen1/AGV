package com.agv.backend.support;

/** 旧版标准车间：14 节点、15 条双向路段。仅作论文对照与测试夹具，不再作为预置模板发布。 */
public final class LegacyTwoWayWorkshop {

    private LegacyTwoWayWorkshop() {
    }

    public static final String JSON = """
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
                {"id": "W1",  "x": 2,  "y": 16, "type": "WORKSTATION", "capacity": 1},
                {"id": "W2",  "x": 10, "y": 16, "type": "WORKSTATION", "capacity": 1},
                {"id": "W3",  "x": 18, "y": 16, "type": "WORKSTATION", "capacity": 1},
                {"id": "B2",  "x": 5,  "y": 16, "type": "BUFFER",    "capacity": 1},
                {"id": "B3",  "x": 13, "y": 16, "type": "BUFFER",    "capacity": 1},
                {"id": "B4",  "x": 21, "y": 16, "type": "BUFFER",    "capacity": 1}
              ],
              "edges": [
                {"id": "E1",  "from": "CH1", "to": "J1", "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E2",  "from": "J1",  "to": "J5", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E3",  "from": "J5",  "to": "J2", "length": 4,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E4",  "from": "J2",  "to": "J3", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E5",  "from": "J3",  "to": "CH2", "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E6",  "from": "CH1", "to": "J4", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E7",  "from": "J4",  "to": "CH2", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E8",  "from": "J4",  "to": "J2", "length": 6,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E9",  "from": "J1",  "to": "W1", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E10", "from": "J2",  "to": "W2", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E11", "from": "J3",  "to": "W3", "length": 8,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E12", "from": "J5",  "to": "B1", "length": 2,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E13", "from": "W1",  "to": "B2", "length": 3,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E14", "from": "W2",  "to": "B3", "length": 3,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E15", "from": "W3",  "to": "B4", "length": 3,  "direction": "TWO_WAY", "speed": 1.0, "capacity": 1}
              ]
            }
            """;
}
