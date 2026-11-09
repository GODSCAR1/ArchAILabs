package com.codeja.master_acdc.identity;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {"catalog.stock-sync.enabled=false"})
@AutoConfigureMockMvc
@Import(IdentityTestContainers.class)
public abstract class IdentityIntegrationSupport {}
