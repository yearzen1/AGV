package com.agv.backend.support;

/**
 * 预置路网模板。前端「套用模板」直接读这些数据；也可由管理员把模板导入后另存为副本。
 * <p>
 * 三张模板都必须是合法的单向环：所有路段单向、所有节点度数 ≥ 2、有向图强连通。
 * 这是「结构无死锁」的前提，推导见 {@code docs/explanation/结构无死锁设计.md}。
 * 自动驾驶式仓储（Kiva 类）用同一套约束：单向网格 + 站点在网格上 + 不做支路。
 */
public final class RoadNetTemplates {

    private RoadNetTemplates() {
    }

    /** 标准车间：8 节点单向环，三条巷道串联成圈，两个充电位容量 4，环容量 14。 */
    public static final String STANDARD_WORKSHOP = """
            {
              "nodes": [
                {"id": "CH1", "x": 2,  "y": 2,  "type": "CHARGER",     "capacity": 4},
                {"id": "J1",  "x": 2,  "y": 9,  "type": "JUNCTION",    "capacity": 1},
                {"id": "W1",  "x": 2,  "y": 16, "type": "WORKSTATION", "capacity": 1},
                {"id": "W2",  "x": 10, "y": 16, "type": "WORKSTATION", "capacity": 1},
                {"id": "W3",  "x": 18, "y": 16, "type": "WORKSTATION", "capacity": 1},
                {"id": "J2",  "x": 18, "y": 9,  "type": "JUNCTION",    "capacity": 1},
                {"id": "CH2", "x": 18, "y": 2,  "type": "CHARGER",     "capacity": 4},
                {"id": "J3",  "x": 10, "y": 2,  "type": "JUNCTION",    "capacity": 1}
              ],
              "edges": [
                {"id": "E1", "from": "CH1", "to": "J1", "length": 7, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E2", "from": "J1",  "to": "W1", "length": 7, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E3", "from": "W1",  "to": "W2", "length": 8, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E4", "from": "W2",  "to": "W3", "length": 8, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E5", "from": "W3",  "to": "J2", "length": 7, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E6", "from": "J2",  "to": "CH2", "length": 7, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E7", "from": "CH2", "to": "J3", "length": 8, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E8", "from": "J3",  "to": "CH1", "length": 8, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1}
              ]
            }
            """;

    /** 直线车间：去程与回程两条单向线拼成一个环，适合第一次跑通仿真，环容量 16。 */
    public static final String LINE_WORKSHOP = """
            {
              "nodes": [
                {"id": "CH1", "x": 0,  "y": 0,  "type": "CHARGER",     "capacity": 4},
                {"id": "W1",  "x": 10, "y": 0,  "type": "WORKSTATION", "capacity": 1},
                {"id": "W2",  "x": 20, "y": 0,  "type": "WORKSTATION", "capacity": 1},
                {"id": "W3",  "x": 30, "y": 0,  "type": "WORKSTATION", "capacity": 1},
                {"id": "CH2", "x": 40, "y": 0,  "type": "CHARGER",     "capacity": 4},
                {"id": "U1",  "x": 40, "y": 10, "type": "JUNCTION",    "capacity": 1},
                {"id": "U2",  "x": 30, "y": 10, "type": "JUNCTION",    "capacity": 1},
                {"id": "U3",  "x": 20, "y": 10, "type": "JUNCTION",    "capacity": 1},
                {"id": "U4",  "x": 10, "y": 10, "type": "JUNCTION",    "capacity": 1},
                {"id": "U5",  "x": 0,  "y": 10, "type": "JUNCTION",    "capacity": 1}
              ],
              "edges": [
                {"id": "E1",  "from": "CH1", "to": "W1", "length": 10, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E2",  "from": "W1",  "to": "W2", "length": 10, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E3",  "from": "W2",  "to": "W3", "length": 10, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E4",  "from": "W3",  "to": "CH2", "length": 10, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E5",  "from": "CH2", "to": "U1", "length": 10, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E6",  "from": "U1",  "to": "U2", "length": 10, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E7",  "from": "U2",  "to": "U3", "length": 10, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E8",  "from": "U3",  "to": "U4", "length": 10, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E9",  "from": "U4",  "to": "U5", "length": 10, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E10", "from": "U5",  "to": "CH1", "length": 10, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1}
              ]
            }
            """;

    /**
     * 高容量车间：和标准车间同一套环，但工位容量为 2。
     * <p>
     * 等待的车可以直接停到工位上，不必堵在环里，吞吐更高。节点容量也计入环容量，
     * 所以环容量从 14 提高到 17；车队上限仍由充电位容量（8 个停放位）决定。
     */
    public static final String WIDE_STATION_WORKSHOP = """
            {
              "nodes": [
                {"id": "CH1", "x": 2,  "y": 2,  "type": "CHARGER",     "capacity": 4},
                {"id": "J1",  "x": 2,  "y": 9,  "type": "JUNCTION",    "capacity": 1},
                {"id": "W1",  "x": 2,  "y": 16, "type": "WORKSTATION", "capacity": 2},
                {"id": "W2",  "x": 10, "y": 16, "type": "WORKSTATION", "capacity": 2},
                {"id": "W3",  "x": 18, "y": 16, "type": "WORKSTATION", "capacity": 2},
                {"id": "J2",  "x": 18, "y": 9,  "type": "JUNCTION",    "capacity": 1},
                {"id": "CH2", "x": 18, "y": 2,  "type": "CHARGER",     "capacity": 4},
                {"id": "J3",  "x": 10, "y": 2,  "type": "JUNCTION",    "capacity": 1}
              ],
              "edges": [
                {"id": "E1", "from": "CH1", "to": "J1", "length": 7, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E2", "from": "J1",  "to": "W1", "length": 7, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E3", "from": "W1",  "to": "W2", "length": 8, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E4", "from": "W2",  "to": "W3", "length": 8, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E5", "from": "W3",  "to": "J2", "length": 7, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E6", "from": "J2",  "to": "CH2", "length": 7, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E7", "from": "CH2", "to": "J3", "length": 8, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1},
                {"id": "E8", "from": "J3",  "to": "CH1", "length": 8, "direction": "ONE_WAY", "speed": 1.0, "capacity": 1}
              ]
            }
            """;
}
