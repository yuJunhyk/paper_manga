package com.readmanga.backend.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 논문 처리 전용 실행기(벌크헤드): 한 번에 하나만 처리하고 나머지는 대기 줄에 세운다.
 * AI 서버가 동시에 하나만 답을 만들어서, 여러 작업이 함께 돌면 모두 느려지고 시간 초과가 연쇄됐다(39·40번).
 */
@Configuration
public class AsyncConfig {

    @Bean("jobExecutor")
    ThreadPoolTaskExecutor jobExecutor(@Value("${readmanga.jobs.queue-capacity}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);              // 작업 담당자 1명
        executor.setMaxPoolSize(1);               // 바빠져도 담당자를 늘리지 않는다
        executor.setQueueCapacity(queueCapacity); // 대기 줄 길이. 꽉 차면 TaskRejectedException으로 거절
        executor.setThreadNamePrefix("job-");     // 로그에 [job-1]로 찍혀 어느 담당자가 했는지 보인다
        return executor;
    }
}
