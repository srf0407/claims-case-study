package com.claimsprocessing.claims.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import org.springframework.beans.factory.annotation.Autowired;

@AnalyzeClasses(
        packages = "com.claimsprocessing.claims",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String CONFIG = "Config";
    private static final String ADAPTER = "Adapter";
    private static final String APPLICATION = "Application";
    private static final String DOMAIN = "Domain";

    @ArchTest
    static final ArchRule layers_follow_the_dependency_rule = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            // Remove withOptionalLayers(true) once each layer has classes.
            .withOptionalLayers(true)
            .layer(CONFIG).definedBy("com.claimsprocessing.claims.config..")
            .layer(ADAPTER).definedBy("com.claimsprocessing.claims.adapter..")
            .layer(APPLICATION).definedBy("com.claimsprocessing.claims.application..")
            .layer(DOMAIN).definedBy("com.claimsprocessing.claims.domain..")
            .whereLayer(CONFIG).mayNotBeAccessedByAnyLayer()
            .whereLayer(CONFIG).mayOnlyAccessLayers(ADAPTER, APPLICATION, DOMAIN)
            .whereLayer(ADAPTER).mayOnlyBeAccessedByLayers(CONFIG)
            .whereLayer(ADAPTER).mayOnlyAccessLayers(APPLICATION, DOMAIN)
            .whereLayer(APPLICATION).mayOnlyBeAccessedByLayers(ADAPTER, CONFIG)
            .whereLayer(APPLICATION).mayOnlyAccessLayers(DOMAIN)
            .whereLayer(DOMAIN).mayOnlyBeAccessedByLayers(APPLICATION, ADAPTER, CONFIG)
            .whereLayer(DOMAIN).mayNotAccessAnyLayer();

    @ArchTest
    static final ArchRule domain_is_free_of_spring_jakarta_and_jackson = noClasses()
            .that().resideInAPackage("com.claimsprocessing.claims.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule application_is_free_of_spring = noClasses()
            .that().resideInAPackage("com.claimsprocessing.claims.application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule adapters_do_not_depend_on_each_other = CompositeArchRule.of(
                    slices().matching("com.claimsprocessing.claims.adapter.out.(*)..")
                            .should().notDependOnEachOther())
            .and(noClasses()
                    .that().resideInAPackage("com.claimsprocessing.claims.adapter.in..")
                    .should().dependOnClassesThat().resideInAPackage("com.claimsprocessing.claims.adapter.out.."))
            .and(noClasses()
                    .that().resideInAPackage("com.claimsprocessing.claims.adapter.out..")
                    .should().dependOnClassesThat().resideInAPackage("com.claimsprocessing.claims.adapter.in.."));

    @ArchTest
    static final ArchRule no_field_injection = noFields()
            .should().beAnnotatedWith(Autowired.class);
}
