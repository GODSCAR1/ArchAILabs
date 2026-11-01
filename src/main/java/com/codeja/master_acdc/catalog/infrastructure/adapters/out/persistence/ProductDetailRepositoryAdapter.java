package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductReview;
import com.codeja.master_acdc.catalog.domain.model.ProductReviewPage;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.domain.model.ProductVariant;
import com.codeja.master_acdc.catalog.domain.port.ProductDetailRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ProductDetailRepositoryAdapter implements ProductDetailRepository {

  private final EntityManager entityManager;

  public ProductDetailRepositoryAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public Optional<ProductDetail> findBySku(String sku) {
    List<Object[]> productRows = queryProduct(sku);
    if (productRows.isEmpty()) {
      return Optional.empty();
    }
    Object[] product = productRows.getFirst();
    UUID productId = (UUID) product[0];
    List<ProductVariant> variants = queryVariants(productId);
    Map<String, String> attributes = queryAttributes(productId);
    List<Object[]> reviewRows = queryRecentReviews(productId);
    ProductRating rating = rating(reviewRows);
    List<ProductReview> reviews =
        reviewRows.stream().filter(row -> row[2] != null).map(this::review).toList();
    return Optional.of(
        new ProductDetail(
            productId,
            (String) product[1],
            (String) product[2],
            ((Number) product[3]).longValue(),
            ProductStatus.valueOf((String) product[4]),
            new Category((UUID) product[5], (String) product[6]),
            ((Number) product[7]).intValue(),
            variants,
            attributes,
            rating,
            reviews));
  }

  @Override
  public Optional<ProductReviewPage> findReviewsBySku(String sku, int page, int size) {
    List<?> product =
        entityManager
            .createNativeQuery("select id from products where sku = :sku")
            .setParameter("sku", sku)
            .getResultList();
    if (product.isEmpty()) {
      return Optional.empty();
    }
    UUID productId = (UUID) product.getFirst();
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                "select id, author, rating, comment, created_at from reviews "
                    + "where product_id = :productId order by created_at desc, id asc")
            .setParameter("productId", productId)
            .setFirstResult(page * size)
            .setMaxResults(size)
            .getResultList();
    Number total =
        (Number)
            entityManager
                .createNativeQuery("select count(*) from reviews where product_id = :productId")
                .setParameter("productId", productId)
                .getSingleResult();
    long totalElements = total.longValue();
    return Optional.of(
        new ProductReviewPage(
            rows.stream().map(ProductDetailPersistenceMapper::toReview).toList(),
            page,
            size,
            totalElements,
            (int) Math.ceil((double) totalElements / size)));
  }

  private List<Object[]> queryProduct(String sku) {
    return entityManager
        .createNativeQuery(
            "select p.id, p.sku, p.name, p.price, p.status, c.id, c.name, "
                + "coalesce(s.available, 0) from products p "
                + "join categories c on c.id = p.category_id "
                + "left join stock s on s.product_id = p.id where p.sku = :sku")
        .setParameter("sku", sku)
        .getResultList();
  }

  private List<ProductVariant> queryVariants(UUID productId) {
    return entityManager
        .createNativeQuery(
            "select id, sku_suffix, color, size, additional_price from product_variants "
                + "where product_id = :productId order by id")
        .setParameter("productId", productId)
        .getResultList()
        .stream()
        .map(row -> ProductDetailPersistenceMapper.toVariant((Object[]) row))
        .toList();
  }

  private Map<String, String> queryAttributes(UUID productId) {
    Map<String, String> attributes = new LinkedHashMap<>();
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                "select name, value from product_attributes where product_id = :productId "
                    + "order by name")
            .setParameter("productId", productId)
            .getResultList();
    rows.forEach(row -> attributes.put((String) row[0], (String) row[1]));
    return attributes;
  }

  private List<Object[]> queryRecentReviews(UUID productId) {
    return entityManager
        .createNativeQuery(
            "select a.average, a.total, r.id, r.author, r.rating, r.comment, r.created_at "
                + "from (select coalesce(round(avg(rating)::numeric, 2), 0) average, "
                + "count(*) total from reviews where product_id = :productId) a "
                + "left join (select id, author, rating, comment, created_at, "
                + "row_number() over (order by created_at desc, id asc) position "
                + "from reviews where product_id = :productId) r on r.position <= 5 "
                + "order by r.created_at desc nulls last, r.id asc")
        .setParameter("productId", productId)
        .getResultList();
  }

  private ProductRating rating(List<Object[]> rows) {
    Object[] row = rows.getFirst();
    return new ProductRating(((BigDecimal) row[0]).setScale(2), ((Number) row[1]).longValue());
  }

  private ProductReview review(Object[] row) {
    return ProductDetailPersistenceMapper.toReview(
        new Object[] {row[2], row[3], row[4], row[5], row[6]});
  }
}
