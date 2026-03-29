package com.example.alertengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class AlertengineApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlertengineApplication.class, args);
	}

}
