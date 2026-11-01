package com.ciemsa.sigma;

import org.springframework.boot.SpringApplication;

public class TestSigmaApplication {

  public static void main(String[] args) {
    SpringApplication.from(SigmaApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
