package com.agv.algorithm.sim;

import java.util.List;

/**
 * 任务分配策略。两种算法共用同一避障策略，只在这里不同，保证对比公平。
 */
public interface TaskAssigner {

    /** 字典码，与 {@code sys_dict.algorithm} 对应。 */
    String name();

    /** 中文名，写进报表。 */
    String displayName();

    /**
     * 从候选任务里挑一个给指定的 AGV。
     *
     * @param currentNodeId AGV 当前所在节点
     * @return 选中的任务；候选为空时返回 null
     */
    Task choose(String currentNodeId, List<Task> candidates);
}
