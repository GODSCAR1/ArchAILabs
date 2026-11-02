package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductReview;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.domain.model.ProductVariant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogProductMongoMapperTest {

  @Test
  @DisplayName("Ficha agregada disponible")
  void should_map_all_catalog_detail_fields_when_mapping_to_mongo_document() {
    ProductDetail source = productDetail(2, 3);

    CatalogProduct document =
        CatalogProductMongoMapper.toDocument(source, Instant.parse("2026-01-01T00:00:00Z"), 4);

    assertThat(document.id()).isEqualTo(source.id());
    assertThat(document.sku()).isEqualTo(source.sku());
    assertThat(document.price()).isEqualTo(source.price());
    assertThat(document.category().id()).isEqualTo(source.category().id());
    assertThat(document.availableStock()).isEqualTo(source.stock());
    assertThat(document.variants()).hasSize(2);
    assertThat(document.attributes()).isEqualTo(source.attributes());
    assertThat(document.rating().average()).isEqualByComparingTo(source.rating().average());
    assertThat(document.latestReviews()).hasSize(3);
    assertThat(CatalogProductMongoMapper.toDomain(document)).isEqualTo(source);
  }

  @Test
  @DisplayName("Arrays acotados")
  void should_keep_only_five_latest_reviews_when_mapping_document() {
    ProductDetail source = productDetail(2, 7);

    CatalogProduct document =
        CatalogProductMongoMapper.toDocument(source, Instant.parse("2026-01-01T00:00:00Z"), 4);

    assertThat(document.latestReviews()).hasSize(5);
    assertThat(document.latestReviews().getFirst().id())
        .isEqualTo(source.recentReviews().getFirst().id());
    assertThat(document.latestReviews().getLast().id())
        .isEqualTo(source.recentReviews().get(4).id());
  }

  private ProductDetail productDetail(int variantCount, int reviewCount) {
    UUID productId = UUID.randomUUID();
    List<ProductVariant> variants =
        java.util.stream.IntStream.range(0, variantCount)
            .mapToObj(
                index -> new ProductVariant(UUID.randomUUID(), "-" + index, "Azul", "M", index))
            .toList();
    List<ProductReview> reviews =
        java.util.stream.IntStream.range(0, reviewCount)
            .mapToObj(
                index ->
                    new ProductReview(
                        UUID.randomUUID(),
                        "Ana",
                        5,
                        "Buena",
                        Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index)))
            .toList();
    return new ProductDetail(
        productId,
        "SKU-" + productId,
        "Camisa",
        189900,
        ProductStatus.ACTIVE,
        new Category(UUID.randomUUID(), "Camisas"),
        18,
        variants,
        Map.of("material", "Lino"),
        new ProductRating(new BigDecimal("4.67"), reviewCount),
        reviews);
  }
}
