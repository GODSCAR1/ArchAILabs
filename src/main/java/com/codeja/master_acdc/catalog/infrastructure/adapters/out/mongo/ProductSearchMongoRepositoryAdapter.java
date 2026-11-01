package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchRow;
import com.codeja.master_acdc.catalog.domain.port.ProductSearchRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class ProductSearchMongoRepositoryAdapter implements ProductSearchRepository {

  private final MongoTemplate mongoTemplate;

  public ProductSearchMongoRepositoryAdapter(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public ProductSearchPage search(ProductSearchCriteria criteria) {
    Query countQuery = query(criteria);
    long totalElements = mongoTemplate.count(countQuery, CatalogProduct.class);
    Query pageQuery = query(criteria).with(pageRequest(criteria));
    projectPageFields(pageQuery);
    applyNameCollation(pageQuery, criteria);
    List<ProductSearchRow> rows =
        mongoTemplate.find(pageQuery, CatalogProductSearchView.class).stream()
            .map(CatalogProductSearchMongoMapper::toRow)
            .toList();
    return new ProductSearchPage(
        rows,
        criteria.page(),
        criteria.size(),
        totalElements,
        (int) Math.ceil((double) totalElements / criteria.size()));
  }

  private Query query(ProductSearchCriteria criteria) {
    List<Criteria> filters = new ArrayList<>();
    addTextFilter(filters, criteria);
    addCategoryFilter(filters, criteria);
    addPriceFilters(filters, criteria);
    addStatusFilter(filters, criteria);
    addStockFilter(filters, criteria);
    return filters.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(filters));
  }

  private void addTextFilter(List<Criteria> filters, ProductSearchCriteria criteria) {
    if (criteria.text() != null && !criteria.text().isBlank()) {
      filters.add(Criteria.where("name").regex(Pattern.quote(criteria.text()), "i"));
    }
  }

  private void addCategoryFilter(List<Criteria> filters, ProductSearchCriteria criteria) {
    if (criteria.categoryId() != null) {
      filters.add(Criteria.where("category._id").is(criteria.categoryId()));
    }
  }

  private void addPriceFilters(List<Criteria> filters, ProductSearchCriteria criteria) {
    if (criteria.minPrice() != null) {
      filters.add(Criteria.where("price").gte(criteria.minPrice()));
    }
    if (criteria.maxPrice() != null) {
      filters.add(Criteria.where("price").lte(criteria.maxPrice()));
    }
  }

  private void addStatusFilter(List<Criteria> filters, ProductSearchCriteria criteria) {
    if (!criteria.statuses().isEmpty()) {
      filters.add(Criteria.where("status").in(criteria.statuses()));
    }
  }

  private void addStockFilter(List<Criteria> filters, ProductSearchCriteria criteria) {
    if (criteria.inStock()) {
      filters.add(Criteria.where("availableStock").gt(0));
    }
  }

  private PageRequest pageRequest(ProductSearchCriteria criteria) {
    ProductSearchOrder.Direction direction = criteria.order().direction();
    Sort.Direction sortDirection =
        switch (direction) {
          case ASC -> Sort.Direction.ASC;
          case DESC -> Sort.Direction.DESC;
        };
    Sort sort =
        Sort.by(sortDirection, criteria.order().field()).and(Sort.by(Sort.Direction.ASC, "id"));
    return PageRequest.of(criteria.page(), criteria.size(), sort);
  }

  private void projectPageFields(Query query) {
    query
        .fields()
        .include("id")
        .include("name")
        .include("price")
        .include("availableStock")
        .include("category.name")
        .include("status");
  }

  private void applyNameCollation(Query query, ProductSearchCriteria criteria) {
    if ("name".equals(criteria.order().field())) {
      query.collation(Collation.simple());
    }
  }
}
