package com.swiftdrop.courier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Courier Service: Courier profiles, availability, assigned deliveries and delivery status updates. */
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class CourierServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CourierServiceApplication.class, args);
	}

}
