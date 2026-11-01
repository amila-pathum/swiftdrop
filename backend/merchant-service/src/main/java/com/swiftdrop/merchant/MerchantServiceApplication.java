package com.swiftdrop.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Merchant Service: Merchant registration, approval, API credentials, orders and webhook configuration. */
@SpringBootApplication
public class MerchantServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MerchantServiceApplication.class, args);
	}

}
