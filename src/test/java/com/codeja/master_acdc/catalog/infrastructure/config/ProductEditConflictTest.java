package com.codeja.master_acdc.catalog.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.catalog.application.port.EditProductUseCase;
import com.codeja.master_acdc.catalog.domain.exception.ConcurrentProductModificationException;
import com.codeja.master_acdc.catalog.domain.model.Price;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductName;
import com.codeja.master_acdc.catalog.domain.model.Sku;
import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "catalog.store=postgres")
@Import(ProductEditConflictTest.ConflictConfiguration.class)
@Testcontainers
class ProductEditConflictTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ProductRepository productRepository;
  @Autowired ProductEditingRepository productEditingRepository;
  @Autowired EditProductUseCase editProductUseCase;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from products");
  }

  @Test
  @DisplayName("Conflicto detectado al guardar")
  void should_reject_when_version_changes_before_save() {
    Product original =
        Product.create(
            new ProductName("Original"), new Sku("ABC-123"), Price.of(new BigDecimal("100")));
    productRepository.save(original);
    Product current = productEditingRepository.findById(original.id()).orElseThrow();

    assertThatThrownBy(
            () ->
                editProductUseCase.edit(
                    original.id(), "Rejected", new BigDecimal("250"), current.version()))
        .isInstanceOf(ConcurrentProductModificationException.class);

    assertThat(
            jdbcTemplate.queryForObject(
                "select name from products where id = ?", String.class, original.id()))
        .isEqualTo("Original");
    assertThat(
            jdbcTemplate.queryForObject(
                "select price from products where id = ?", Long.class, original.id()))
        .isEqualTo(100L);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ConflictConfiguration {

    @Bean
    @Primary
    ProductEditingRepository conflictRepository(
        @Qualifier("productEditingRepository") ProductEditingRepository delegate,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager) {
      return new ConflictRepository(delegate, jdbcTemplate, transactionManager);
    }
  }

  private static final class ConflictRepository implements ProductEditingRepository {

    private final ProductEditingRepository delegate;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNew;

    private ConflictRepository(
        ProductEditingRepository delegate,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager) {
      this.delegate = delegate;
      this.jdbcTemplate = jdbcTemplate;
      this.requiresNew = new TransactionTemplate(transactionManager);
      this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Optional<Product> findById(UUID productId) {
      return delegate.findById(productId);
    }

    @Override
    public Product save(Product product) {
      requiresNew.executeWithoutResult(
          status ->
              jdbcTemplate.update(
                  "update products set version = version + 1 where id = ?", product.id()));
      return delegate.save(product);
    }
  }
}
