package com.agent.monito;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
		org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})
public class MonitoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MonitoApplication.class, args);
	}

}
