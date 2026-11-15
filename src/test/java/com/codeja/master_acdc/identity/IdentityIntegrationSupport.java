package com.codeja.master_acdc.identity;

import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductRepository;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.domain.port.UserSummaryQuery;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import com.codeja.master_acdc.inventory.api.InventoryFacade;
import com.codeja.master_acdc.inventory.domain.port.StockReservationRepository;
import com.codeja.master_acdc.ordering.api.CurrentOrderActor;
import com.codeja.master_acdc.ordering.domain.port.OrderCancelledPublisher;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Base única de los {@code @SpringBootTest} que la extienden (lab S14, docs/lab-s14.md): un solo
 * contexto de Spring por JVM.
 *
 * <p>Las subclases NO añaden configuración: ni {@code @MockitoBean}/{@code @MockitoSpyBean}, ni
 * {@code @TestPropertySource}/{@code properties}, ni {@code @Import}, ni {@code @ActiveProfiles}.
 * Cualquiera de ellas cambia la clave de la cache de contextos y arranca otro contexto completo
 * (Postgres, Redis, Mongo). Lo que un test necesite se añade aquí, para todos.
 *
 * <p>Los spies envuelven el bean real: sin stub se comportan igual que él, y Mockito los resetea
 * tras cada test (stubs e invocaciones). Úsalos para inyectar fallos o fijar la hora ({@code
 * doReturn(instante).when(spiedClock).instant()}), nunca para sustituir lo que se prueba: el emisor
 * de tokens ({@code JwtEncoder}) es real, con la clave efímera de {@code
 * GlobalIdentityTestExtension}.
 */
@SpringBootTest(
    properties = {
      "catalog.stock-sync.enabled=false",
      // La matriz de seguridad recorre los endpoints del catálogo con la cache activa (lab S13).
      "catalog.cache.enabled=true",
      // Tests de número de consultas: estadísticas de Hibernate y captura del SQL (SqlCapture).
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector="
          + "com.codeja.master_acdc.identity.SqlCapture"
    })
@AutoConfigureMockMvc
@Import({IdentityTestContainers.class, IdentityTestBeans.class})
public abstract class IdentityIntegrationSupport {
  /** Reloj de identity ({@code identityClock}): caducidades de access y refresh. */
  @MockitoSpyBean protected Clock spiedClock;

  /** Versión de sesión en Redis: fallos del estado compartido (fail closed). */
  @MockitoSpyBean protected RedisSessionVersions spiedSessionVersions;

  // Fronteras de autorización: un acceso denegado no llega al puerto (MethodAuthorizationTest).
  @MockitoSpyBean protected UserAccounts spiedAccounts;
  @MockitoSpyBean protected UserSummaryQuery spiedUserSummaries;
  @MockitoSpyBean protected ProductRepository spiedProducts;
  @MockitoSpyBean protected ProductEditingRepository spiedProductEditing;
  @MockitoSpyBean protected StockReservationRepository spiedStockReservations;
  @MockitoSpyBean protected CurrentOrderActor spiedOrderActor;
  @MockitoSpyBean protected OrderRepository spiedOrders;
  @MockitoSpyBean protected OrderSummaryQuery spiedOrderSummaries;
  @MockitoSpyBean protected OrderCancelledPublisher spiedCancellations;
  @MockitoSpyBean protected InventoryFacade spiedInventory;

  @AfterEach
  void stopSqlCapture() {
    SqlCapture.stop();
  }
}
