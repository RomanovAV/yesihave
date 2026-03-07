package org.avromanov.yesihave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YesIHaveApplication {
    public static void main(String[] args) {
        SpringApplication.run(YesIHaveApplication.class, args);
    }
}
