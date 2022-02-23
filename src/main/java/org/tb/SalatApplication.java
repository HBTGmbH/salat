package org.tb;

import java.util.stream.Stream;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.support.DatabaseStartupValidator;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableJpaRepositories
@EnableJpaAuditing
@EnableTransactionManagement
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
public class SalatApplication {

  public static void main(String[] args) {
    SpringApplication.run(SalatApplication.class, args);
  }

  @Bean
  public static BeanFactoryPostProcessor dependsOnPostProcessor() {
    return bf -> {
      // Let beans that need the database depend on the DatabaseStartupValidator
      // like the JPA EntityManagerFactory or Flyway
      /* maybe for future
      String[] flyway = bf.getBeanNamesForType(Flyway.class);
      Stream.of(flyway)
          .map(bf::getBeanDefinition)
          .forEach(it -> it.setDependsOn("databaseStartupValidator"));
       */

      String[] jpa = bf.getBeanNamesForType(EntityManagerFactory.class);
      Stream.of(jpa)
          .map(bf::getBeanDefinition)
          .forEach(it -> it.setDependsOn("databaseStartupValidator"));
    };
  }

  @Bean
  public DatabaseStartupValidator databaseStartupValidator(DataSource dataSource) {
    var dsv = new DatabaseStartupValidator();
    dsv.setDataSource(dataSource);
    dsv.setInterval(15);
    dsv.setTimeout(120);
    return dsv;
  }

}
