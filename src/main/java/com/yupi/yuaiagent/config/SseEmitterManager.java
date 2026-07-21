package com.yupi.yuaiagent.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE Emitter 管理器
 * - 心跳保活：定期发送心跳注释，防止 Nginx/网关超时断开
 * - 用户取消：通过 chatId 取消正在进行的 SSE 流
 * - 自动清理：emitter 完成/超时/错误时自动清理资源
 * <p>
 * 简历亮点：企业级流式输出，支持心跳保活与用户主动取消
 */
@Component
@Slf4j
public class SseEmitterManager {

    private static final long HEARTBEAT_INTERVAL_SECONDS = 25;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /**
     * 包装 SseEmitter，添加心跳保活和取消支持
     *
     * @param chatId  会话ID，用于取消
     * @param emitter 原始 SseEmitter
     * @return 同一个 SseEmitter（已注册心跳和清理回调）
     */
    public SseEmitter wrap(String chatId, SseEmitter emitter) {
        if (chatId == null || emitter == null) {
            return emitter;
        }

        emitters.put(chatId, emitter);

        // 注册清理回调
        emitter.onCompletion(() -> cleanup(chatId));
        emitter.onTimeout(() -> {
            log.info("SSE timeout for chatId: {}", chatId);
            cleanup(chatId);
        });
        emitter.onError(e -> {
            log.warn("SSE error for chatId: {}: {}", chatId, e.getMessage());
            cleanup(chatId);
        });

        // 启动心跳保活
        startHeartbeat(chatId, emitter);

        log.info("SSE emitter registered for chatId: {} (total active: {})", chatId, emitters.size());
        return emitter;
    }

    /**
     * 创建新的 SseEmitter（带心跳和取消支持）
     */
    public SseEmitter create(String chatId, long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);
        return wrap(chatId, emitter);
    }

    /**
     * 取消指定会话的 SSE 流
     *
     * @param chatId 会话ID
     * @return true 如果成功取消
     */
    public boolean cancel(String chatId) {
        SseEmitter emitter = emitters.get(chatId);
        if (emitter != null) {
            try {
                emitter.complete();
                log.info("SSE cancelled by user for chatId: {}", chatId);
                cleanup(chatId);
                return true;
            } catch (Exception e) {
                log.warn("Failed to cancel SSE for chatId: {}: {}", chatId, e.getMessage());
            }
        }
        return false;
    }

    /**
     * 获取当前活跃的 SSE 连接数
     */
    public int getActiveCount() {
        return emitters.size();
    }

    /**
     * 获取所有活跃的 chatId
     */
    public Set<String> getActiveChatIds() {
        return emitters.keySet();
    }

    /**
     * 启动心跳保活
     */
    private void startHeartbeat(String chatId, SseEmitter emitter) {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                log.debug("Heartbeat failed for chatId: {} (likely completed): {}", chatId, e.getMessage());
                cleanup(chatId);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        heartbeatTasks.put(chatId, task);
    }

    /**
     * 清理指定 chatId 的资源
     */
    private void cleanup(String chatId) {
        emitters.remove(chatId);
        ScheduledFuture<?> task = heartbeatTasks.remove(chatId);
        if (task != null) {
            task.cancel(false);
        }
    }

    @PreDestroy
    public void onDestroy() {
        log.info("Shutting down SSE manager, cleaning up {} active connections", emitters.size());
        heartbeatTasks.values().forEach(t -> t.cancel(false));
        emitters.values().forEach(e -> {
            try {
                e.complete();
            } catch (Exception ignored) {
            }
        });
        emitters.clear();
        heartbeatTasks.clear();
        scheduler.shutdown();
    }
}
