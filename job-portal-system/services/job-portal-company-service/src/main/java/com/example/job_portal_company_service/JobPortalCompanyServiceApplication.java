package com.example.job_portal_company_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class JobPortalCompanyServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobPortalCompanyServiceApplication.class, args);
	}

}
