package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "catalog.store=postgres")
class ProductReviewPostgresIT extends ProductReviewQueryIT {}
