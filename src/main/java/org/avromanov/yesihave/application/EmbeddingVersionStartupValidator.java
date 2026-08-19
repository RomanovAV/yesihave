package org.avromanov.yesihave.application;

import org.avromanov.yesihave.image.EmbeddingPipelineDescriptorProvider;
import org.avromanov.yesihave.persistence.EmbeddingVersionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.embedding",
        name = "require-active-version",
        havingValue = "true",
        matchIfMissing = true
)
public class EmbeddingVersionStartupValidator implements ApplicationRunner {
    private final EmbeddingPipelineDescriptorProvider descriptorProvider;
    private final EmbeddingVersionRepository embeddingVersionRepository;

    public EmbeddingVersionStartupValidator(EmbeddingPipelineDescriptorProvider descriptorProvider,
                                            EmbeddingVersionRepository embeddingVersionRepository) {
        this.descriptorProvider = descriptorProvider;
        this.embeddingVersionRepository = embeddingVersionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        embeddingVersionRepository.ensureActiveForApplication(descriptorProvider.descriptor());
    }
}
