import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Shared between LicenseHeaderHarmonize.java (jbang) and the Gradle "licenseHeaderHarmonize"
// Spotless step (server/buildSrc) - edit this one file, both paths pick it up automatically.
//
// Existing license headers vary in comment-fence width, presence of the NOTICE paragraph, and
// license URL wording (accumulated as the EPL-2.0 boilerplate evolved over the project's
// history). This rewrites the boilerplate around each file's leading "Copyright (c) ..." line
// to the current canonical form, leaving that copyright line itself untouched since it records
// real contribution history (holder, year) that formatting must not overwrite. A file with no
// license header at all gets one added, dated with the current year and attributed to
// "Contributors to the Eclipse Foundation" - the default for anything without prior attribution.
public class LicenseHeaderHarmonizeCore {

    private static final Pattern LEADING_COMMENT = Pattern.compile("\\A(/\\*.*?\\*/)", Pattern.DOTALL);
    private static final Pattern COPYRIGHT_LINE = Pattern.compile("^\\s*\\*\\s*(Copyright \\(c\\).*?)\\s*$", Pattern.MULTILINE);
    private static final String DEFAULT_COPYRIGHT = "Copyright (c) " + Year.now() + " Contributors to the Eclipse Foundation";

    public static String fix(String source) {
        Matcher comment = LEADING_COMMENT.matcher(source);
        if (comment.find()) {
            String block = comment.group(1);
            if (block.contains("Copyright (c)") && block.contains("Eclipse Public License")) {
                Matcher copyright = COPYRIGHT_LINE.matcher(block);
                if (copyright.find()) {
                    String canonical = canonicalHeader(copyright.group(1));
                    return canonical.equals(block) ? source : canonical + source.substring(comment.end());
                }
            }
        }
        return canonicalHeader(DEFAULT_COPYRIGHT) + "\n" + source;
    }

    private static final int WIDTH = 79;

    private static String canonicalHeader(String copyrightLine) {
        String openFence = "*".repeat(WIDTH - 1);
        String closeFence = "*".repeat(WIDTH - 2);
        return "/" + openFence + "\n"
            + " * " + copyrightLine + "\n"
            + " *\n"
            + " * See the NOTICE file(s) distributed with this work for additional\n"
            + " * information regarding copyright ownership.\n"
            + " *\n"
            + " * This program and the accompanying materials are made available under the\n"
            + " * terms of the Eclipse Public License 2.0 which is available at\n"
            + " * https://www.eclipse.org/legal/epl-2.0.\n"
            + " *\n"
            + " * SPDX-License-Identifier: EPL-2.0\n"
            + " " + closeFence + "/";
    }
}
