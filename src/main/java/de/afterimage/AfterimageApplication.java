package de.afterimage;

import de.afterimage.config.AfterimageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AfterimageProperties.class)
public class AfterimageApplication {

    public static void main(String[] args) {
        SpringApplication.run(AfterimageApplication.class, args);
    }
}

