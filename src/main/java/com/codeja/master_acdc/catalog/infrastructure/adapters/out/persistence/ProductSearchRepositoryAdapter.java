package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import com.codeja.master_acdc.catalog.domain.port.ProductSearchRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public class ProductSearchRepositoryAdapter implements ProductSearchRepository {

  private final ProductJpaRepository productJpaRepository;

  public ProductSearchRepositoryAdapter(ProductJpaRepository productJpaRepository) {
    this.productJpaRepository = productJpaRepository;
  }

  @Override
  public ProductSearchPage search(ProductSearchCriteria criteria) {
    Page<ProductJpaEntity> page =
        productJpaRepository.findAll(specification(criteria), pageRequest(criteria));
    return new ProductSearchPage(
        page.getContent().stream().map(ProductPersistenceMapper::toRow).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  private Specification<ProductJpaEntity> specification(ProductSearchCriteria criteria) {
    List<Specification<ProductJpaEntity>> filters = new ArrayList<>();
    if (criteria.text() != null && !criteria.text().isBlank()) {
      filters.add(ProductSpecifications.textContains(criteria.text()));
    }
    if (criteria.categoryId() != null) {
      filters.add(ProductSpecifications.categoryIs(criteria.categoryId()));
    }
    if (criteria.minPrice() != null) {
      filters.add(ProductSpecifications.priceAtLeast(criteria.minPrice()));
    }
    if (criteria.maxPrice() != null) {
      filters.add(ProductSpecifications.priceAtMost(criteria.maxPrice()));
    }
    if (!criteria.statuses().isEmpty()) {
      filters.add(ProductSpecifications.statusIn(criteria.statuses()));
    }
    if (criteria.inStock()) {
      filters.add(ProductSpecifications.hasStock());
    }
    filters.add(ProductSpecifications.fetchAssociations());
    return Specification.allOf(filters);
  }

  private PageRequest pageRequest(ProductSearchCriteria criteria) {
    Sort.Direction direction =
        criteria.order().direction()
                == com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder.Direction.ASC
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    Sort sort = Sort.by(direction, criteria.order().field()).and(Sort.by(Sort.Direction.ASC, "id"));
    return PageRequest.of(criteria.page(), criteria.size(), sort);
  }
}
