package com.agv.algorithm.sim;

/**
 * AGV 运行状态。前端按此着色与显示文字。
 */
public enum AgvStatus {
    /** 空闲：在节点上，无任务或等待任务分配 */
    IDLE,
    /** 行驶中 */
    MOVING,
    /** 冲突等待：已到达节点，但要进入的下一条弧/节点被占 */
    WAITING,
    /** 装卸货中 */
    LOADING,
    /** 回充后停靠 */
    CHARGING,
    /** 全部任务完成后无任务可接且已回位 */
    DONE
}
