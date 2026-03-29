package com.example.monitoringapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class MonitoringapiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MonitoringapiApplication.class, args);
	}

}
