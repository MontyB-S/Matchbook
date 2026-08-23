package com.monty.matchbook;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.monty.matchbook");

    @Nested
    class EngineIsFrameworkFree {

        @Test
        void noSpring() {
            noClasses()
                    .that()
                    .resideInAPackage("..engine..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..")
                    .because("the engine must stay portable and fast to test")
                    .check(CLASSES);
        }

        @Test
        void noPersistence() {
            noClasses()
                    .that()
                    .resideInAPackage("..engine..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("the book is in memory; persistence belongs to the components around it")
                    .check(CLASSES);
        }

        @Test
        void noKafka() {
            noClasses()
                    .that()
                    .resideInAPackage("..engine..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..")
                    .because("the engine is reached through an adapter, never directly")
                    .check(CLASSES);
        }

        @Test
        void noLombok() {
            noClasses()
                    .that()
                    .resideInAPackage("..engine..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("lombok..")
                    .check(CLASSES);
        }

        @Test
        void noJackson() {
            noClasses()
                    .that()
                    .resideInAPackage("..engine..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("tools.jackson..", "com.fasterxml.jackson..")
                    .because("wire formats live in event/, not in the hot path")
                    .check(CLASSES);
        }
    }

    @Nested
    class ComponentsStayIndependent {

        @Test
        void theEngineKnowsNothingOfTheComponentsAroundIt() {
            noClasses()
                    .that()
                    .resideInAPackage("..engine..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..gateway..", "..matching..", "..position..", "..event..")
                    .because("dependencies point inwards")
                    .check(CLASSES);
        }

        @Test
        void theGatewayDoesNotReachIntoPositions() {
            noClasses()
                    .that()
                    .resideInAPackage("..gateway..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..position..")
                    .because("components communicate only via Kafka")
                    .check(CLASSES);
        }

        @Test
        void theGatewayDoesNotReachIntoMatching() {
            noClasses()
                    .that()
                    .resideInAPackage("..gateway..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..matching..")
                    .because("components communicate only via Kafka")
                    .check(CLASSES);
        }

        @Test
        void matchingDoesNotReachIntoTheGateway() {
            noClasses()
                    .that()
                    .resideInAPackage("..matching..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..gateway..")
                    .because("components communicate only via Kafka")
                    .check(CLASSES);
        }

        @Test
        void theGatewayDoesNotUseTheBooksInternals() {
            noClasses()
                    .that()
                    .resideInAPackage("..gateway..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..engine.book..")
                    .because("the gateway never touches a book; it publishes commands")
                    .check(CLASSES);
        }
    }
}
