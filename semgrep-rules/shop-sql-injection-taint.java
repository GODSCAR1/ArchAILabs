// Fixtures detectan cada patrón y aceptan su remediación
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

class SqlTaintFixture {
  private JdbcTemplate jdbc;

  void fromCriteria(EntityManager em, OrderListingCriteria criteria) {
    String sql = "select * from orders order by " + criteria.sort();
    // ruleid: shop-sql-injection-taint
    em.unwrap(org.hibernate.Session.class).createNativeQuery(sql, Object[].class);
  }

  void fromRequest(@RequestParam("sort") String value, EntityManager em) {
    String fragment = value;
    String sql = "select * from orders order by " + fragment;
    // ruleid: shop-sql-injection-taint
    em.createNativeQuery(sql);
    // ok: shop-sql-injection-taint
    em.createQuery("select o from Orders o where o.name = :name").setParameter("name", value);
  }

  List<Map<String, Object>> fromPath(@PathVariable String column) {
    // ruleid: shop-sql-injection-taint
    return jdbc.queryForList("select * from orders order by " + column);
  }

  List<Map<String, Object>> bound(@RequestParam String status) {
    // ok: shop-sql-injection-taint
    return jdbc.queryForList("select * from orders where status = ?", status);
  }
}
