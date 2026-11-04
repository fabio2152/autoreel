package com.autoreel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AutoReelApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutoReelApplication.class, args);
	}

}
