package com.gatekeeper.repository;

import com.gatekeeper.exception.ConflictException;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.repository.dto.CreateRepositoryRequest;
import com.gatekeeper.repository.dto.UpdateRepositoryRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final OrganizationService organizationService;

    public List<Repository> findAll() {
        return repositoryRepository.findAll();
    }

    public Repository findById(Long id) {
        return repositoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with id: " + id));
    }

    @Transactional
    public Repository create(CreateRepositoryRequest request) {
        if (repositoryRepository.existsByFullNameIgnoreCase(request.fullName())) {
            throw new ConflictException("A repository named '" + request.fullName() + "' already exists.");
        }
        Repository repository = Repository.builder()
                .organization(organizationService.getDefaultOrganization())
                .name(request.name())
                .fullName(request.fullName())
                .description(request.description())
                .active(true)
                .build();
        return repositoryRepository.save(repository);
    }

    @Transactional
    public Repository update(Long id, UpdateRepositoryRequest request) {
        Repository repository = findById(id);
        repository.setName(request.name());
        repository.setDescription(request.description());
        repository.setActive(request.active());
        return repositoryRepository.save(repository);
    }

    @Transactional
    public void delete(Long id) {
        Repository repository = findById(id);
        repositoryRepository.delete(repository);
    }
}
