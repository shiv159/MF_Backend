package com.mutualfunds.api.mutual_fund;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(
        packages = "com.mutualfunds.api.mutual_fund",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final Pattern FEATURE_PATTERN = Pattern.compile("\\.features\\.([^.]+)\\.");

    @ArchTest
    static final ArchRule no_cross_feature_persistence_access =
            classes()
                    .that().resideInAnyPackage("..features..", "..shared..")
                    .should(new ArchCondition<>("access only same-feature persistence packages") {
                        @Override
                        public void check(JavaClass item, ConditionEvents events) {
                            String sourceFeature = featureName(item.getPackageName())
                                    .orElse(item.getPackageName().contains(".shared.") ? "shared" : null);
                            if (sourceFeature == null) {
                                return;
                            }

                            for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                                JavaClass target = dependency.getTargetClass();
                                String targetPackage = target.getPackageName();
                                if (!targetPackage.contains(".features.") || !targetPackage.contains(".persistence.")) {
                                    continue;
                                }

                                Optional<String> targetFeature = featureName(targetPackage);
                                if (targetFeature.isEmpty()) {
                                    continue;
                                }

                                if (!sourceFeature.equals(targetFeature.get())) {
                                    String message = String.format(
                                            "%s depends on %s (%s -> %s)",
                                            item.getFullName(),
                                            target.getFullName(),
                                            sourceFeature,
                                            targetFeature.get());
                                    events.add(SimpleConditionEvent.violated(item, message));
                                }
                            }
                        }
                    });

    private static Optional<String> featureName(String packageName) {
        Matcher matcher = FEATURE_PATTERN.matcher(packageName);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }
}
