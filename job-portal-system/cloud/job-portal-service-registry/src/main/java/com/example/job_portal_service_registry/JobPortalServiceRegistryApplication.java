package com.example.job_portal_service_registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class JobPortalServiceRegistryApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobPortalServiceRegistryApplication.class, args);
	}

}
