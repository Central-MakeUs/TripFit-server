package com.tripfit.tripfit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tripfit.tripfit.common.exception.ErrorCode;
import com.tripfit.tripfit.user.domain.SocialProvider;
import jakarta.persistence.Id;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.tripfit.tripfit");
  }

  @Test
  void domainLayerDoesNotDependOnControllerOrService() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..service..");
    rule.check(classes);
  }

  @Test
  void controllersDoNotDependOnRepository() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..");
    rule.check(classes);
  }

  @Test
  void repositoryInterfacesAreOnlyDataAccessAbstractions() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..repository")
            .should()
            .beInterfaces();
    rule.check(classes);
  }

  @Test
  void noFieldInjection() {
    ArchRule rule =
        noFields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("com.tripfit.tripfit..")
            .should()
            .beAnnotatedWith(Autowired.class);
    rule.check(classes);
  }

  @Test
  void controllersAreNotTransactional() {
    ArchRule rule =
        noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .beAnnotatedWith(Transactional.class);
    rule.check(classes);
  }

  @Test
  void entityPrimaryKeysAreUuid() {
    ArchRule rule = fields().that().areAnnotatedWith(Id.class).should().haveRawType(UUID.class);
    rule.check(classes);
  }

  @Test
  void errorCodeClassesImplementErrorCodeInterface() {
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("ErrorCode")
            .and()
            .areNotInterfaces()
            .should()
            .implement(ErrorCode.class);
    rule.check(classes);
  }

  @Test
  void commonPackageDoesNotDependOnOtherDomains() {

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("com.tripfit.tripfit.common..")
            .should()
            .dependOnClassesThat(
                JavaClass.Predicates.resideInAnyPackage(
                    "com.tripfit.tripfit.auth..",
                    "com.tripfit.tripfit.user..",
                    "com.tripfit.tripfit.trip..",
                    "com.tripfit.tripfit.notification..")
                    .and(
                        DescribedPredicate.not(
                            JavaClass.Predicates.assignableTo(
                                SocialProvider.class))));
    rule.check(classes);
  }
}
