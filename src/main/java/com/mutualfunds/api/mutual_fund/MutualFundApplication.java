package com.mutualfunds.api.mutual_fund;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.mutualfunds.api.mutual_fund")
public class MutualFundApplication {

	public static void main(String[] args) {
		SpringApplication.run(MutualFundApplication.class, args);
	}

}
