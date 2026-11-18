package com.foodloop.service;

import com.foodloop.dto.AcceptedCategoriesResponse;
import com.foodloop.exception.BadRequestException;
import com.foodloop.repository.NgoAcceptedCategoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class NgoAcceptedCategoryService {

    static final int MAX_CATEGORIES = 20;

    private final NgoAcceptedCategoryRepository repository;
    private final AuditService auditService;

    public NgoAcceptedCategoryService(NgoAcceptedCategoryRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AcceptedCategoriesResponse get(Long ngoId) {
        return toResponse(repository.find(ngoId));
    }

    @Transactional
    public AcceptedCategoriesResponse replace(Long ngoId, List<Integer> categoryIds) {
        if (categoryIds == null || categoryIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("categoryIds must be a list of category ids");
        }
        Set<Integer> ids = new TreeSet<>(categoryIds);
        if (ids.size() > MAX_CATEGORIES) {
            throw new BadRequestException("Choose at most " + MAX_CATEGORIES + " categories");
        }
        if (repository.countExisting(ids) != ids.size()) {
            throw new BadRequestException("One or more category ids do not exist");
        }
        Set<Integer> before = repository.find(ngoId);
        repository.replace(ngoId, ids);
        auditService.log(ngoId, "UPDATE_ACCEPTED_CATEGORIES", "NGO_PROFILE", ngoId,
                Map.of("categoryIds", before), Map.of("categoryIds", ids));
        return toResponse(ids);
    }

    private static AcceptedCategoriesResponse toResponse(Set<Integer> ids) {
        return new AcceptedCategoriesResponse(new ArrayList<>(ids), ids.isEmpty());
    }
}