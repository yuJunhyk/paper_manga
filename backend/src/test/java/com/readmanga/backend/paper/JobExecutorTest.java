package com.readmanga.backend.paper;

import com.readmanga.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 논문 처리 전용 실행기(jobExecutor): 한 번에 하나만 처리하고, 나머지는 정해진 길이만큼만 줄 세운다(벌크헤드).
 * AI 서버가 동시에 하나만 답을 만들어서, 여러 작업이 함께 돌면 모두 느려지고 시간 초과가 연쇄됐다(39·40번).
 */
@SpringBootTest(properties = "readmanga.jobs.queue-capacity=2")
@Import(TestcontainersConfiguration.class)  // 실제 DB 대신 일회용 테스트 DB
class JobExecutorTest {

    @Autowired
    ApplicationContext context;

    @Test
    void 논문_처리_실행기는_담당자_1명에_설정한_길이의_대기_줄을_가진다() {
        ThreadPoolTaskExecutor executor = context.getBean("jobExecutor", ThreadPoolTaskExecutor.class);

        assertThat(executor.getCorePoolSize()).isEqualTo(1);
        assertThat(executor.getMaxPoolSize()).isEqualTo(1);
        assertThat(executor.getQueueCapacity()).as("readmanga.jobs.queue-capacity 설정값").isEqualTo(2);
    }

    @Test
    void 논문_처리와_다시_시도는_이_실행기로_돈다() throws NoSuchMethodException {
        assertThat(PaperJobProcessor.class.getMethod("process", Long.class, byte[].class, String.class).getAnnotation(Async.class).value())
                .isEqualTo("jobExecutor");
        assertThat(PaperJobProcessor.class.getMethod("retry", Long.class).getAnnotation(Async.class).value())
                .isEqualTo("jobExecutor");
    }

    @Test
    void 동시에_하나만_돌고_줄이_꽉_차면_더_받지_않는다() throws InterruptedException {
        ThreadPoolTaskExecutor executor = context.getBean("jobExecutor", ThreadPoolTaskExecutor.class);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        Runnable job = () -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                release.await(5, TimeUnit.SECONDS);  // 앞 작업이 오래 걸리는 상황
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
                finished.incrementAndGet();
            }
        };

        executor.execute(job);  // 처리 중 1개
        executor.execute(job);  // 대기 1번
        executor.execute(job);  // 대기 2번 (줄 꽉 참)
        assertThatThrownBy(() -> executor.execute(job)).isInstanceOf(TaskRejectedException.class);

        release.countDown();
        for (int i = 0; i < 50 && finished.get() < 3; i++) {
            Thread.sleep(100);
        }
        assertThat(finished.get()).isEqualTo(3);
        assertThat(maxRunning.get()).as("동시에 돈 작업 수의 최댓값").isEqualTo(1);
    }
}
