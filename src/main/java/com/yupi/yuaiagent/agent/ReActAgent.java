package com.yupi.yuaiagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 标记是否已收到 AI 的最终回答（不需要再调用工具）
     */
    private boolean finalAnswerReceived = false;

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考和行动
     * - think() 返回 false：AI 已给出最终回答，标记 finalAnswerReceived
     * - think() 返回 true：执行工具调用，返回工具结果，继续循环
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                // AI 不需要调用工具，返回最终回答
                String lastThought = getLastThought();
                if (lastThought != null && !lastThought.isEmpty()) {
                    // 标记已收到最终回答，BaseAgent 循环将据此退出
                    this.finalAnswerReceived = true;
                    return lastThought;
                }
                this.finalAnswerReceived = true;
                return "思考完成";
            }
            // 需要调用工具，执行行动并返回工具结果（不标记 finalAnswerReceived，循环继续）
            return act();
        } catch (Exception e) {
            // 记录异常日志
            e.printStackTrace();
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /**
     * 获取最后一次思考的内容
     * 子类可以重写此方法返回具体的思考内容
     */
    protected String getLastThought() {
        return null;
    }

    /**
     * 检查是否已收到最终回答
     * 供 BaseAgent 循环判断是否退出
     */
    @Override
    protected boolean isFinalAnswerReceived() {
        return this.finalAnswerReceived;
    }

}
