// Fixtures detectan cada patrón y aceptan su remediación
// La regla solo se aplica a **/adapters/in/web/**: los modelos de dominio y
// persistencia (fuera de esa ruta) pueden tener customerId.
class WebController {
  // ruleid: shop-no-customer-id-param
  void query(@RequestParam String customerId) {}
  // ruleid: shop-no-customer-id-param
  void queryNamed(@RequestParam("customerId") String owner) {}
  // ruleid: shop-no-customer-id-param
  void path(@PathVariable(value = "customerId") String id) {}
  // ruleid: shop-no-customer-id-param
  void header(@RequestHeader(name = "customerId") String id) {}
  // ruleid: shop-no-customer-id-param
  void body(@RequestBody String customerId) {}
  // ok: shop-no-customer-id-param
  void own(Account account) { var customerId = account.customerId(); }
  // ok: shop-no-customer-id-param
  UUID resolve(Account account) { UUID customerId = account.customerId(); return customerId; }
}
class CreateOrderRequest {
  // ruleid: shop-no-customer-id-param
  String customerId;
}
class Payload {
  // ruleid: shop-no-customer-id-param
  private UUID customerId;
}
// ruleid: shop-no-customer-id-param
record OrderRequest(String customerId, String item) {}
// ruleid: shop-no-customer-id-param
record CheckoutData(UUID customerId) {}
// ok: shop-no-customer-id-param
record OrderLineBody(UUID productId, int quantity) {}
