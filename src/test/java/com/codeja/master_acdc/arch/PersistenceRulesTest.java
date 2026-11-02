package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import jakarta.persistence.Version;
import java.lang.reflect.Method;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;

@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.DoNotIncludeTests.class)
class PersistenceRulesTest {

  @ArchTest
  static final ArchRule domain_and_application_have_no_transactional_annotations =
      classes()
          .that()
          .resideInAnyPackage("..domain..", "..application..")
          .should(haveNoTransactionalAnnotations());

  @ArchTest
  static final ArchRule find_for_update_methods_use_pessimistic_lock_and_timeout =
      classes()
          .that()
          .areAssignableTo(Repository.class)
          .should(haveProtectedFindForUpdateMethods());

  private static ArchCondition<JavaClass> haveVersionField() {
    return new ArchCondition<>("have a field annotated with @Version") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (item.getFields().stream().noneMatch(field -> field.isAnnotatedWith(Version.class))) {
          events.add(
              new SimpleConditionEvent(
                  item, false, item.getName() + " has no field annotated with @Version"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> haveNoTransactionalAnnotations() {
    return new ArchCondition<>("not use @Transactional on classes or methods") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (item.isAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            || item.isAnnotatedWith(jakarta.transaction.Transactional.class)) {
          events.add(new SimpleConditionEvent(item, false, item.getName() + " is transactional"));
        }
        item.getMethods().stream()
            .filter(PersistenceRulesTest::isTransactional)
            .forEach(method -> reportTransactionalMethod(item, method, events));
      }
    };
  }

  private static boolean isTransactional(JavaMethod method) {
    return method.isAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
        || method.isAnnotatedWith(jakarta.transaction.Transactional.class);
  }

  private static void reportTransactionalMethod(
      JavaClass item, JavaMethod method, ConditionEvents events) {
    events.add(
        new SimpleConditionEvent(
            item, false, item.getName() + "." + method.getName() + " is transactional"));
  }

  private static ArchCondition<JavaClass> haveProtectedFindForUpdateMethods() {
    return new ArchCondition<>("protect findForUpdate methods") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        item.getMethods().stream()
            .filter(method -> method.getName().startsWith("findForUpdate"))
            .forEach(method -> checkLockMetadata(item, method, events));
      }
    };
  }

  private static void checkLockMetadata(JavaClass item, JavaMethod method, ConditionEvents events) {
    Method reflectedMethod = method.reflect();
    Lock lock = reflectedMethod.getAnnotation(Lock.class);
    QueryHints hints = reflectedMethod.getAnnotation(QueryHints.class);
    boolean hasWriteLock = lock != null && lock.value() == LockModeType.PESSIMISTIC_WRITE;
    boolean hasTimeout =
        hints != null
            && java.util.Arrays.stream(hints.value())
                .map(QueryHint::name)
                .anyMatch("jakarta.persistence.lock.timeout"::equals);
    if (!hasWriteLock || !hasTimeout) {
      events.add(new SimpleConditionEvent(item, false, item.getName() + "." + method.getName()));
    }
  }
}
