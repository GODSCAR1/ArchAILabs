package com.codeja.master_acdc.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.domain.exception.DuplicateSkuException;
import com.codeja.master_acdc.domain.model.Price;
import com.codeja.master_acdc.domain.model.Product;
import com.codeja.master_acdc.domain.model.ProductName;
import com.codeja.master_acdc.domain.model.Sku;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ProductRepositoryAdapterIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ProductRepositoryAdapter adapter;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from products");
  }

  @Test
  void should_throw_domain_duplicate_exception_when_two_products_share_sku_without_prior_check() {
    Product first =
        Product.create(
            new ProductName("First"), new Sku("CAF-500"), Price.of(new BigDecimal("100")));
    Product second =
        Product.create(
            new ProductName("Second"), new Sku("caf-500"), Price.of(new BigDecimal("200")));

    adapter.save(first);

    assertThatThrownBy(() -> adapter.save(second))
        .isInstanceOf(DuplicateSkuException.class)
        .isNotInstanceOf(DataIntegrityViolationException.class);

    assertThat(jdbcTemplate.queryForObject("select count(*) from products", Integer.class))
        .isEqualTo(1);
  }
}
