package com.ihdrs.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class IhdrsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(IhdrsBackendApplication.class, args);
	}

}
