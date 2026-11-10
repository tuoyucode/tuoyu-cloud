package org.tuoyu.workflow.handler;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.tuoyu.workflow.api.event.ProcessDeleteEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 流程事件的发布时机。两类接收方：单体中业务模块在同一进程内用 {@code @TransactionalEventListener} 接收；
 * 微服务中消息总线用普通的 {@code @EventListener} 接收后立即投递到其他服务，没有事务保护。
 */
@DisplayName("流程事件：办理事务提交后才发布，回滚时不发布")
class FlowProcessEventHandlerTest {

    private AnnotationConfigApplicationContext context;
    private FlowProcessEventHandler handler;
    private RecordingTransactionManager transactionManager;
    private TransactionTemplate transaction;
    private Received received;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        // 流程事件按服务名标记来源（spring.application.name）
        context.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("test", Map.of("spring.application.name", "tuoyu-workflow")));
        context.register(TestConfig.class);
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        handler = context.getBean(FlowProcessEventHandler.class);
        transactionManager = context.getBean(RecordingTransactionManager.class);
        transaction = new TransactionTemplate(transactionManager);
        received = context.getBean(Received.class);
    }

    @AfterEach
    void tearDown() {
        new SpringUtil().setApplicationContext(null);
        context.close();
    }

    @Test
    @DisplayName("事务提交后，总线与进程内的事务监听器都收到事件，收到时事务已提交")
    void publishedAfterCommit() {
        transaction.executeWithoutResult(status -> {
            handler.processDeleteHandler("leave", "1");
            assertThat(received.bus).as("提交前总线不应收到事件").isEmpty();
            assertThat(received.local).as("提交前进程内的监听器不应收到事件").isEmpty();
        });

        assertThat(received.bus).containsExactly("1@committed");
        assertThat(received.local).containsExactly("1@committed");
    }

    @Test
    @DisplayName("事务回滚时不发布事件")
    void notPublishedOnRollback() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            handler.processDeleteHandler("leave", "1");
            throw new IllegalStateException("办理失败");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(received.bus).isEmpty();
        assertThat(received.local).isEmpty();
    }

    @Test
    @DisplayName("没有事务时立即发布")
    void publishedImmediatelyWithoutTransaction() {
        handler.processDeleteHandler("leave", "1");

        assertThat(received.bus).containsExactly("1@no-transaction");
        assertThat(received.local).containsExactly("1@no-transaction");
    }

    @Test
    @DisplayName("同一事务中的多个事件按发布顺序到达")
    void keepsPublishOrder() {
        transaction.executeWithoutResult(status -> {
            handler.processDeleteHandler("leave", "1");
            handler.processDeleteHandler("leave", "2");
            handler.processDeleteHandler("leave", "3");
        });

        assertThat(received.bus).containsExactly("1@committed", "2@committed", "3@committed");
        assertThat(received.local).containsExactly("1@committed", "2@committed", "3@committed");
    }

    @Test
    @DisplayName("提交后某个事件发布失败时，不影响已提交的结果与其他事件")
    void failureAfterCommitDoesNotBreakCommit() {
        assertThatCode(() -> transaction.executeWithoutResult(status -> {
            handler.processDeleteHandler("leave", Received.FAILING_ID);
            handler.processDeleteHandler("leave", "2");
        })).doesNotThrowAnyException();

        assertThat(transactionManager.committed).isTrue();
        assertThat(received.bus).containsExactly("2@committed");
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        FlowProcessEventHandler flowProcessEventHandler(ApplicationEventPublisher publisher) {
            return new FlowProcessEventHandler(publisher);
        }

        @Bean
        Received received(RecordingTransactionManager transactionManager) {
            return new Received(transactionManager);
        }
    }

    /**
     * 只记录提交与回滚的事务管理器
     */
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private volatile boolean inTransaction;
        private volatile boolean committed;
        private volatile boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            inTransaction = true;
            committed = false;
            rolledBack = false;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            inTransaction = false;
        }

        String state() {
            if (!inTransaction) {
                return "no-transaction";
            }
            return committed ? "committed" : "uncommitted";
        }
    }

    /**
     * 两类接收方，记录收到的业务ID与收到时的事务状态
     */
    static class Received {

        static final String FAILING_ID = "failing";

        private final RecordingTransactionManager transactionManager;
        final List<String> bus = new CopyOnWriteArrayList<>();
        final List<String> local = new CopyOnWriteArrayList<>();

        Received(RecordingTransactionManager transactionManager) {
            this.transactionManager = transactionManager;
        }

        /**
         * 与消息总线接收本地事件的方式相同：普通的事件监听器，收到即投递
         */
        @EventListener
        public void onBus(ProcessDeleteEvent event) {
            if (FAILING_ID.equals(event.getBusinessId())) {
                throw new IllegalStateException("总线投递失败");
            }
            bus.add(event.getBusinessId() + "@" + transactionManager.state());
        }

        /**
         * 与单体中业务模块的接收方式相同
         */
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
        public void onLocal(ProcessDeleteEvent event) {
            if (FAILING_ID.equals(event.getBusinessId())) {
                return;
            }
            local.add(event.getBusinessId() + "@" + transactionManager.state());
        }
    }

}
