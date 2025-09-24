package org.tb;

import static org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import java.util.concurrent.Executor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.request.RequestAttributes;

@Configuration
@EnableJpaRepositories
@EnableJpaAuditing
@EnableTransactionManagement
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
@EnableWebSecurity
@EnableAsync
@EnableScheduling
public class SalatApplication implements AsyncConfigurer {

  public static void main(String[] args) {
    SpringApplication.run(SalatApplication.class, args);
  }

  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
    taskExecutor.setCorePoolSize(1);
    taskExecutor.setMaxPoolSize(1);
    taskExecutor.setQueueCapacity(10000);
    taskExecutor.setThreadNamePrefix("AsyncExecutor-");

    // pass request and session scopes to tasks
    taskExecutor.setTaskDecorator(new TaskDecorator() {
      @Override
      public Runnable decorate(Runnable runnable) {
        RequestAttributes context = currentRequestAttributes();
        return () -> {
          try {
            setRequestAttributes(context);
            runnable.run();
          } finally {
            resetRequestAttributes();
          }
        };
      }
    });
    taskExecutor.initialize();

    // pass security context to tasks
    return new DelegatingSecurityContextAsyncTaskExecutor(taskExecutor);
  }

}
