package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "catalog.store=postgres")
class ProductSearchPostgresIT extends ProductSearchIT {

  @Test
  @DisplayName("Lecturas por defecto en PostgreSQL")
  void should_read_from_postgres_when_postgres_mode_is_selected() throws Exception {
    mockMvc.perform(get("/api/products")).andExpect(status().isOk());
  }
}
