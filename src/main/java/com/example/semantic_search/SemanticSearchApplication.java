package com.example.semantic_search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SemanticSearchApplication {

	public static void main(String[] args) {
		SpringApplication.run(SemanticSearchApplication.class, args);
	}

}
