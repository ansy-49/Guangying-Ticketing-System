package com.guangying.service.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务边界后的外部资源回调。
 *
 * <p>Redis、消息通知等非事务资源不能早于数据库提交。该组件把成功补偿放到
 * {@code afterCommit}，把预占资源释放放到回滚完成后执行。</p>
 */
@Slf4j
@Component
public class TransactionCallbacks {

    public void register(Runnable afterCommit, Runnable afterRollback) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            runSafely("afterCommit(no transaction)", afterCommit);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely("afterCommit", afterCommit);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    runSafely("afterRollback", afterRollback);
                }
            }
        });
    }

    public void afterCommit(Runnable callback) {
        register(callback, null);
    }

    private void runSafely(String phase, Runnable callback) {
        if (callback == null) {
            return;
        }
        try {
            callback.run();
        } catch (Exception e) {
            log.error("[TransactionCallbacks] {} callback failed", phase, e);
        }
    }
}
