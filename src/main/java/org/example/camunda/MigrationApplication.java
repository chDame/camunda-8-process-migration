package org.example.camunda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
public class MigrationApplication {

  public static void main(String[] args) {
    SpringApplication.run(MigrationApplication.class, args);
  }
}
