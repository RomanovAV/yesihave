package org.avromanov.yesihave.cli;

import org.avromanov.yesihave.YesIHaveApplication;
import org.avromanov.yesihave.application.ReindexEmbeddingsService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

public class ReindexEmbeddingsCli {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(YesIHaveApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(args);

        int processed = 0;
        try {
            ReindexEmbeddingsService service = context.getBean(ReindexEmbeddingsService.class);
            processed = service.reindexAll();
            System.out.println("Reindex completed. Records processed: " + processed);
        } finally {
            SpringApplication.exit(context, () -> 0);
        }
    }
}
