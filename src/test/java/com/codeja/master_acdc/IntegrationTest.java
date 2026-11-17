package com.codeja.master_acdc;

import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductRepository;
import com.codeja.master_acdc.identity.SqlCapture;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.domain.port.UserSummaryQuery;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import com.codeja.master_acdc.inventory.api.InventoryFacade;
import com.codeja.master_acdc.inventory.domain.port.StockReservationRepository;
import com.codeja.master_acdc.ordering.api.CurrentOrderActor;
import com.codeja.master_acdc.ordering.domain.port.OrderCancelledPublisher;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.Clock;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;

/**
 * Base común de integración. Las subclases no añaden configuración de contexto, propiedades,
 * imports ni mocks: cualquier diferencia crea otro contexto y otro juego de contenedores.
 *
 * <p>Los spies envuelven los beans reales y, sin stub, se comportan como ellos; Mockito los
 * restablece después de cada test. No sustituyen lo que se prueba: los tokens usan el emisor y el
 * verificador reales con la clave efímera de {@link GlobalIdentityTestExtension}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, IntegrationTestBeans.class})
public abstract class IntegrationTest {
  static final String TRUNCATE_DOMAIN =
      """
      TRUNCATE TABLE customers, order_confirmation_outbox, order_lines, orders, password_reset_token, refresh_token, stock, user_account
      RESTART IDENTITY CASCADE
      """;
  @MockitoSpyBean protected Clock spiedClock;
  @MockitoSpyBean protected RedisSessionVersions spiedSessionVersions;
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

  @Autowired private JdbcTemplate cleanupJdbc;
  @Autowired private MongoTemplate cleanupMongo;
  @Autowired private StringRedisTemplate cleanupRedis;
  @Autowired private CacheManager cleanupCaches;

  @Autowired
  @Qualifier("wiremock") private GenericContainer<?> wiremock;

  @BeforeEach
  void cleanStores() {
    cleanupJdbc.execute(TRUNCATE_DOMAIN);
    for (String collection : cleanupMongo.getCollectionNames()) {
      if (!collection.startsWith("system.")) {
        cleanupMongo.getCollection(collection).deleteMany(new Document());
      }
    }
    cleanupRedis.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushDb();
              return null;
            });
    for (String name : cleanupCaches.getCacheNames()) {
      var cache = cleanupCaches.getCache(name);
      if (cache != null) {
        cache.clear();
      }
    }
    wiremock().resetToDefaultMappings();
    wiremock().resetRequests();
  }

  protected WireMock wiremock() {
    return new WireMock(wiremock.getHost(), wiremock.getMappedPort(8080));
  }

  protected void runSql(String... statements) {
    for (String statement : statements) {
      cleanupJdbc.execute(statement);
    }
  }

  @AfterEach
  void stopSqlCapture() {
    SqlCapture.stop();
  }
}
