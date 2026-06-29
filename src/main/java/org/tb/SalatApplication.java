package org.tb;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.request.RequestAttributes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.springframework.web.context.request.RequestContextHolder.*;

@Slf4j
@Configuration
@EnableJpaRepositories
@EnableJpaAuditing
@EnableTransactionManagement
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
@EnableWebSecurity
@EnableMethodSecurity
@EnableAsync
@EnableScheduling
public class SalatApplication implements AsyncConfigurer, SchedulingConfigurer {

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
        SnapshotRequestAttributes attributes = new SnapshotRequestAttributes(context);
        return () -> {
          try {
            setRequestAttributes(attributes);
            runnable.run();
          } finally {
            resetRequestAttributes();
            attributes.clear();
          }
        };
      }
    });
    taskExecutor.initialize();

    // pass security context to tasks
    return new DelegatingSecurityContextAsyncTaskExecutor(taskExecutor);
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    taskRegistrar.getCronTaskList().forEach(task -> {
      log.info("Scheduled cron task: {} ({})", task, task.getExpression());
    });
  }

  private static final class SnapshotRequestAttributes implements RequestAttributes {

    private Map<String, Object> requestAttributes = new HashMap<>();
    private Map<String, Object> sessionAttributes = new HashMap<>();

    public SnapshotRequestAttributes(RequestAttributes attributes) {
      Arrays.stream(attributes.getAttributeNames(SCOPE_REQUEST))
              .forEach(key -> this.requestAttributes.put(key, attributes.getAttribute(key, SCOPE_REQUEST)));
      Arrays.stream(attributes.getAttributeNames(SCOPE_SESSION))
              .forEach(key -> this.sessionAttributes.put(key, attributes.getAttribute(key, SCOPE_SESSION)));
    }

    void clear() {
      // maybe this is not required to cleanup heap
      requestAttributes.clear();
      sessionAttributes.clear();
      requestAttributes = null;
      sessionAttributes = null;
    }

    @Override
    public @Nullable Object getAttribute(String name, int scope) {
      return scope == SCOPE_REQUEST ?
              requestAttributes.get(name) : sessionAttributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value, int scope) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeAttribute(String name, int scope) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String[] getAttributeNames(int scope) {
      return scope == SCOPE_REQUEST ?
              requestAttributes.keySet().toArray(new String[0]) : sessionAttributes.keySet().toArray(new String[0]);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback, int scope) {
      // noop
    }

    @Override
    public @Nullable Object resolveReference(String key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getSessionId() {
      return "<none>";
    }

    @Override
    public Object getSessionMutex() {
      return this;
    }
  }
}
