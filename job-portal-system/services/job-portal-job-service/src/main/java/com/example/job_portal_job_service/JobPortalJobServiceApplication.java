package com.example.job_portal_job_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class JobPortalJobServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobPortalJobServiceApplication.class, args);
	}

}
