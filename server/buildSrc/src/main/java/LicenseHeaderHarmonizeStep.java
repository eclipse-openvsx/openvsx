import com.diffplug.spotless.FormatterStep;

import java.io.Serializable;

// Wraps LicenseHeaderHarmonizeCore (shared with the jbang path, see
// scripts/LicenseHeaderHarmonize.java) as a Spotless FormatterStep for use in the Gradle
// "spotless { java { ... } }" block.
public final class LicenseHeaderHarmonizeStep {

    private LicenseHeaderHarmonizeStep() {
    }

    public static FormatterStep create() {
        return FormatterStep.create("licenseHeaderHarmonize", "unused", (Serializable state) -> LicenseHeaderHarmonizeCore::fix);
    }
}
