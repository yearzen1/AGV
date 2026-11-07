package com.agv.algorithm.model;

/**
 * 路网节点类型。
 * 充电位（CHARGER）同时是 AGV 的初始停放位，一个有效路网至少需要一个。
 */
public enum NodeType {
    /** 工位：任务的起点或终点 */
    WORKSTATION,
    /** 充电位：AGV 初始停放位与空闲回位点 */
    CHARGER,
    /** 缓存位：等待避障时的安全停靠点 */
    BUFFER,
    /** 路口 */
    JUNCTION
}
