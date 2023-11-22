package org.tb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableWebSecurity
@EnableJpaAuditing
@EnableJpaRepositories({"de.hbt.salat","org.tb"})
@EntityScan({"de.hbt.salat","org.tb"})
@ComponentScan({"de.hbt.salat","org.tb"})
@EnableTransactionManagement
@SpringBootApplication(exclude = { ErrorMvcAutoConfiguration.class, SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
public class SalatApplication {

  public static void main(String[] args) {
    SpringApplication.run(SalatApplication.class, args);
  }

}
