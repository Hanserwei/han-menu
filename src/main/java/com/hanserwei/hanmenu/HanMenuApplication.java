package com.hanserwei.hanmenu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/** Entry point for the menu modular monolith. */
@EnableAsync
@SpringBootApplication
public class HanMenuApplication {

  /** Starts the HTTP application and verifies its module structure. */
  public static void main(String[] args) {
    SpringApplication.run(HanMenuApplication.class, args);
  }
}
