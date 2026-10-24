package org.avromanov.yesihave.application;

import org.avromanov.yesihave.persistence.CoasterReadRepository;
import org.springframework.stereotype.Service;

@Service
public class CollectionStatisticsService {
    private final CoasterReadRepository repository;

    public CollectionStatisticsService(CoasterReadRepository repository) {
        this.repository = repository;
    }

    public long countUniqueCoasters() {
        return repository.countCoasters();
    }
}
