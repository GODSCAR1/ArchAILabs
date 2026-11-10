// Fixtures detectan cada patrón y aceptan su remediación
class WebController {
  // ruleid: shop-no-customer-id-param
  void query(@RequestParam String customerId) {}
  // ruleid: shop-no-customer-id-param
  void queryNamed(@RequestParam("customerId") String owner) {}
  // ruleid: shop-no-customer-id-param
  void path(@PathVariable(value = "customerId") String id) {}
  // ruleid: shop-no-customer-id-param
  void header(@RequestHeader("customerId") String id) {}
  // ruleid: shop-no-customer-id-param
  void body(@RequestBody String customerId) {}
  // ok: shop-no-customer-id-param
  void own(Account account) { var customerId = account.customerId(); }
}
class CreateOrderRequest {
  // ruleid: shop-no-customer-id-param
  String customerId;
}
// ruleid: shop-no-customer-id-param
record OrderRequest(String customerId, String item) {}
class InternalOrder {
  // ok: shop-no-customer-id-param
  String customerId;
}
