package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ModuleDependenciesTest {
  @ArchTest
  static final ArchRule modules_have_no_cycles =
      slices().matching("com.codeja.master_acdc.(*)..").should().beFreeOfCycles();
}
