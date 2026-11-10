// Fixtures detectan cada patrón y aceptan su remediación
import java.sql.Connection;
import java.sql.Statement;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

class SqlConcatenationFixture {
  private JdbcTemplate jdbc;
  private JdbcClient client;

  void jpa(EntityManager em, String input) {
    // ruleid: shop-sql-concatenation
    em.createQuery("select o from Orders o where o.name = '" + input + "'");
    String sql = "select * from orders order by " + input;
    // ruleid: shop-sql-concatenation
    em.unwrap(org.hibernate.Session.class).createNativeQuery(sql, Object[].class);
    // ok: shop-sql-concatenation
    em.createQuery("select o from Orders o where o.name = :name").setParameter("name", input);
    String message = "hello " + input;
    // ok: shop-sql-concatenation
    em.getCriteriaBuilder().createQuery();
  }

  void springJdbc(String sort, String status) {
    String sql = "select id from orders where status = ? order by " + sort;
    // ruleid: shop-sql-concatenation
    jdbc.queryForList(sql, status);
    // ruleid: shop-sql-concatenation
    jdbc.update("delete from orders where id = " + status);
    // ruleid: shop-sql-concatenation
    client.sql(String.format("select * from orders order by %s", sort));
    // ok: shop-sql-concatenation
    jdbc.queryForList("select id from orders where status = ? order by id", status);
    // ok: shop-sql-concatenation
    client.sql("select * from orders where status = :status").param("status", status);
  }

  void jdbc(Connection connection, Statement statement, String input) throws Exception {
    // ruleid: shop-sql-concatenation
    statement.executeQuery("select * from orders where id = " + input);
    // ruleid: shop-sql-concatenation
    connection.prepareStatement("select * from orders order by " + input);
    // ok: shop-sql-concatenation
    connection.prepareStatement("select * from orders where id = ?");
  }
}
