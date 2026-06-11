package com.example.job_portal_application_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class JobPortalApplicationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobPortalApplicationServiceApplication.class, args);
	}

}
