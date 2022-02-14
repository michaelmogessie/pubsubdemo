package info.michaelmogessie.pubsubdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "info.michaelmogessie" })
public class PubsubdemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(PubsubdemoApplication.class, args);
	}

}
