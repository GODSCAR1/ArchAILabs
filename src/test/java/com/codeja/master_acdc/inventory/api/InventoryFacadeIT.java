package com.codeja.master_acdc.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.inventory.domain.model.ReservationResult;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class InventoryFacadeIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final String A_TEXT = "11111111-1111-1111-1111-111111111111";
  private static final String B_TEXT = "22222222-2222-2222-2222-222222222222";
  private static final String X_TEXT = "99999999-9999-9999-9999-999999999999";

  @Autowired InventoryFacade facade;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Reserva con stock suficiente en todos los productos")
  @Sql(
      statements = {
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A_TEXT + "', 10)",
        "insert into stock (product_id, available) values ('" + B_TEXT + "', 5)"
      })
  void should_apply_reservation_when_all_lines_have_stock() {
    ReservationResult result = facade.reserve(List.of(line(A_TEXT, 3), line(B_TEXT, 2)));

    assertThat(result).isEqualTo(ReservationResult.RESERVED);
    assertThat(availableOf(A_TEXT)).isEqualTo(7);
    assertThat(availableOf(B_TEXT)).isEqualTo(3);
  }

  @Test
  @DisplayName("Reserva con stock insuficiente en algún producto")
  @Sql(
      statements = {
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A_TEXT + "', 10)",
        "insert into stock (product_id, available) values ('" + B_TEXT + "', 1)"
      })
  void should_report_insufficient_and_keep_stock_when_any_line_lacks() {
    ReservationResult result = facade.reserve(List.of(line(A_TEXT, 3), line(B_TEXT, 2)));

    assertThat(result).isEqualTo(ReservationResult.INSUFFICIENT_STOCK);
    assertThat(availableOf(A_TEXT)).isEqualTo(10);
    assertThat(availableOf(B_TEXT)).isEqualTo(1);
  }

  @Test
  @DisplayName("Reserva en la frontera: cantidad igual al stock disponible")
  @Sql(
      statements = {
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A_TEXT + "', 10)"
      })
  void should_leave_zero_stock_when_quantity_equals_available() {
    ReservationResult result = facade.reserve(List.of(line(A_TEXT, 10)));

    assertThat(result).isEqualTo(ReservationResult.RESERVED);
    assertThat(availableOf(A_TEXT)).isZero();
  }

  @Test
  @DisplayName("Reserva en la frontera: cantidad una unidad por encima del stock")
  @Sql(
      statements = {
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A_TEXT + "', 10)"
      })
  void should_reject_and_keep_stock_when_quantity_exceeds_by_one() {
    ReservationResult result = facade.reserve(List.of(line(A_TEXT, 11)));

    assertThat(result).isEqualTo(ReservationResult.INSUFFICIENT_STOCK);
    assertThat(availableOf(A_TEXT)).isEqualTo(10);
  }

  @Test
  @DisplayName("Reserva con producto sin registro de stock")
  @Sql(
      statements = {
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A_TEXT + "', 10)"
      })
  void should_report_unknown_product_when_stock_record_missing() {
    ReservationResult result = facade.reserve(List.of(line(A_TEXT, 3), line(X_TEXT, 1)));

    assertThat(result).isEqualTo(ReservationResult.UNKNOWN_PRODUCT);
    assertThat(availableOf(A_TEXT)).isEqualTo(10);
  }

  @Test
  @DisplayName("Reserva con producto desconocido y otro sin stock")
  @Sql(
      statements = {
        "delete from stock",
        "insert into stock (product_id, available) values ('" + B_TEXT + "', 1)"
      })
  void should_report_unknown_product_before_insufficient_when_both_present() {
    ReservationResult result = facade.reserve(List.of(line(B_TEXT, 2), line(X_TEXT, 1)));

    assertThat(result).isEqualTo(ReservationResult.UNKNOWN_PRODUCT);
    assertThat(availableOf(B_TEXT)).isEqualTo(1);
  }

  @Test
  @DisplayName("Stock liberado vuelve a estar disponible para reservar")
  @Sql(
      statements = {
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A_TEXT + "', 7)"
      })
  void should_make_stock_available_for_reservation_when_released() {
    facade.release(List.of(line(A_TEXT, 3)));

    ReservationResult result = facade.reserve(List.of(line(A_TEXT, 10)));

    assertThat(result).isEqualTo(ReservationResult.RESERVED);
    assertThat(availableOf(A_TEXT)).isZero();
  }

  private static StockLine line(String productId, int quantity) {
    return new StockLine(UUID.fromString(productId), quantity);
  }

  private int availableOf(String productId) {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?",
        Integer.class,
        UUID.fromString(productId));
  }
}
