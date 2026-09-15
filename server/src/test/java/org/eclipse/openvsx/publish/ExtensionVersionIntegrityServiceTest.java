/** ******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.publish;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.ZipFile;

import com.sun.management.ThreadMXBean;
import jakarta.persistence.EntityManager;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.SignatureKeyPair;
import org.eclipse.openvsx.migration.GenerateKeyPairJobService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ArchiveUtil;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.UUIDService;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@MockitoBean(types = { CacheService.class, RepositoryService.class, EntityManager.class })
class ExtensionVersionIntegrityServiceTest {

    @Autowired
    ExtensionVersionIntegrityService integrityService;

    @Autowired
    GenerateKeyPairJobService keyPairService;

    @Test
    void testGenerateSignature() throws IOException {
        var keyPair = keyPairService.generateKeyPair();

        var namespace = new Namespace();
        namespace.setName("foo");

        var extension = new Extension();
        extension.setName("bar");
        extension.setNamespace(namespace);

        var extVersion = new ExtensionVersion();
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform("universal");
        extVersion.setExtension(extension);

        var download = new FileResource();
        download.setExtension(extVersion);

        try (
                var stream = getClass().getResource("ms-python.python-2024.7.11511013.vsix").openStream();
                var extensionFile = new TempFile("ms-python", ".vsix");
                var out = Files.newOutputStream(extensionFile.getPath())
        ) {
            stream.transferTo(out);
            extensionFile.setResource(download);
            try (
                    var signatureFile = integrityService.generateSignature(extensionFile, keyPair);
                    var sigzip = new ZipFile(signatureFile.getPath().toFile());
                    var expectedSigZip = new ZipFile(
                            getClass().getResource("ms-python.python-2024.7.11511013.sigzip").getPath())
            ) {
                var iterator = expectedSigZip.stream().iterator();
                while (iterator.hasNext()) {
                    var expectedEntry = iterator.next();
                    var entry = sigzip.getEntry(expectedEntry.getName());
                    assertNotNull(entry);
                    if (expectedEntry.getName().equals(".signature.manifest")) {
                        try (
                                var expectedFile = ArchiveUtil.readEntry(expectedSigZip, expectedEntry);
                                var actualFile = ArchiveUtil.readEntry(sigzip, entry)
                        ) {
                            assertEquals(
                                    Files.readString(expectedFile.getPath()),
                                    Files.readString(actualFile.getPath()));
                        }
                    }
                }

                var entry = sigzip.getEntry(".signature.sig");
                assertNotNull(entry);
                try (var entryFile = ArchiveUtil.readEntry(sigzip, entry)) {
                    assertTrue(Files.size(entryFile.getPath()) > 0);
                }
            }
        }
    }

    /**
     * A package big enough for the growth pattern below to dominate the measurement, and deliberately not
     * a power of two: a ByteArrayOutputStream filling up with it ends on a 16 MiB array, having allocated
     * every smaller one on the way.
     */
    private static final int PACKAGE_SIZE = 9 * 1024 * 1024;

    /** How the signing used to work, kept here as the thing the shipped implementation is measured against. */
    private static byte[] signByStreaming(TempFile packageFile, SignatureKeyPair keyPair) throws IOException {
        var signer = new Ed25519Signer();
        signer.init(true, new Ed25519PrivateKeyParameters(keyPair.getPrivateKey(), 0));
        try (var in = Files.newInputStream(packageFile.getPath())) {
            int len;
            var buffer = new byte[1024];
            while ((len = in.read(buffer)) > 0) {
                signer.update(buffer, 0, len);
            }
        }

        return signer.generateSignature();
    }

    private static TempFile givenPackageOfSize(int size) throws IOException {
        var packageFile = new TempFile("package", ".vsix");
        var content = new byte[size];
        // Not zeros: an array of them compresses and deduplicates in ways that could flatter one of the
        // two measurements below, and signing hashes the bytes either way.
        new Random(1450).nextBytes(content);
        Files.write(packageFile.getPath(), content);
        return packageFile;
    }

    private static long allocatedBy(ThrowingRunnable body) throws Exception {
        var threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        var id = Thread.currentThread().threadId();
        var before = threads.getThreadAllocatedBytes(id);
        body.run();
        var after = threads.getThreadAllocatedBytes(id);
        // -1 is what the counter reports when it is not measuring. Skipping beats letting a negative
        // reading through, which would satisfy the "allocated little" assertion for the wrong reason.
        assumeTrue(before >= 0 && after >= 0, "JVM stopped reporting per-thread allocation");
        return after - before;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * The signature is the format's, not an implementation detail: reading the package in one go rather
     * than streaming it into the signer has to produce the very same bytes, or every signature already
     * published stops matching the one a re-sign would produce.
     */
    @Test
    void signsExactlyAsTheStreamingSignerDid() throws Exception {
        var keyPair = keyPairService.generateKeyPair();
        try (var packageFile = givenPackageOfSize(64 * 1024)) {
            try (var signatureFile = integrityService.createSignatureFile(packageFile, keyPair)) {
                assertArrayEquals(
                        signByStreaming(packageFile, keyPair),
                        Files.readAllBytes(signatureFile.getPath()));
            }
        }
    }

    /**
     * A signature of the wrong length does not verify - it does not blow up.
     * <p>
     * The low-level verify reads a fixed 64 bytes from the offset it is handed, so a truncated file
     * reaches BouncyCastle as an index out of bounds and a padded one verifies on its first 64 bytes with
     * the remainder ignored. {@code Ed25519Signer}, which this replaced, checked the length itself and
     * answered false; the mirror - the only caller - refuses the package on exactly that answer.
     */
    @Test
    void refusesASignatureOfTheWrongLength() throws Exception {
        var keyPair = keyPairService.generateKeyPair();
        try (
                var packageFile = givenPackageOfSize(64 * 1024);
                var publicKeyFile = givenPublicKeyFile(keyPair);
                var truncated = new TempFile("signature", ".sig")
        ) {
            try (var signatureFile = integrityService.createSignatureFile(packageFile, keyPair)) {
                var signature = Files.readAllBytes(signatureFile.getPath());
                Files.write(truncated.getPath(), Arrays.copyOf(signature, signature.length - 1));
            }

            assertFalse(integrityService.verifyExtensionVersion(packageFile, truncated, publicKeyFile));
        }
    }

    /**
     * And it refuses it without reading the package, which is the order the check has to be in: both
     * files come from a remote registry, and there is nothing to be gained by pulling a few hundred
     * megabytes into memory to check something that has already failed.
     * <p>
     * Asserted by pointing it at a package that does not exist. Reading it would throw rather than merely
     * waste the memory, which turns "did not read it" into something a test can see.
     */
    @Test
    void refusesAWrongLengthSignatureWithoutReadingThePackage() throws Exception {
        var keyPair = keyPairService.generateKeyPair();
        try (
                var publicKeyFile = givenPublicKeyFile(keyPair);
                var truncated = new TempFile("signature", ".sig");
                var missingPackage = new TempFile("package", ".vsix")
        ) {
            Files.write(truncated.getPath(), new byte[63]);
            Files.delete(missingPackage.getPath());

            assertFalse(integrityService.verifyExtensionVersion(missingPackage, truncated, publicKeyFile));
        }
    }

    @Test
    void verifiesASignatureItProduced() throws Exception {
        var keyPair = keyPairService.generateKeyPair();
        try (
                var packageFile = givenPackageOfSize(64 * 1024);
                var publicKeyFile = givenPublicKeyFile(keyPair);
                var signatureFile = integrityService.createSignatureFile(packageFile, keyPair)
        ) {
            assertTrue(integrityService.verifyExtensionVersion(packageFile, signatureFile, publicKeyFile));
        }
    }

    private static TempFile givenPublicKeyFile(SignatureKeyPair keyPair) throws IOException {
        var publicKeyFile = new TempFile("public", ".pem");
        Files.writeString(publicKeyFile.getPath(), keyPair.getPublicKeyText());
        return publicKeyFile;
    }

    /**
     * Why the implementation looks the way it does (#1450). Streaming a package into
     * {@link Ed25519Signer} looks like the memory-safe choice, but pure Ed25519 hashes the message twice,
     * so the signer keeps every byte handed to it - in a {@code ByteArrayOutputStream} that doubles as it
     * fills, allocating each intermediate array on the way. What it costs is therefore a multiple of the
     * package rather than the package, and for the ~300 MB one in #1450 that was the difference between
     * fitting in a 1 GB heap and not.
     * <p>
     * Measured with per-thread allocation counters, so this is bytes allocated rather than peak live set -
     * the doubling shows up in both, and only the former can be read off without a heap dump.
     */
    @Test
    void allocatesThePackageOnceWhereStreamingAllocatedItSeveralTimesOver() throws Exception {
        var threads = ManagementFactory.getThreadMXBean();
        assumeTrue(
                threads instanceof ThreadMXBean sunThreads && sunThreads.isThreadAllocatedMemorySupported(),
                "JVM does not report per-thread allocation");

        // Supported does not mean switched on, and a counter that is off reports -1 rather than failing.
        // HotSpot enables it by default; a JVM started with it disabled gets it turned on here.
        var sunThreads = (ThreadMXBean) threads;
        if (!sunThreads.isThreadAllocatedMemoryEnabled()) {
            sunThreads.setThreadAllocatedMemoryEnabled(true);
        }
        assumeTrue(sunThreads.isThreadAllocatedMemoryEnabled(), "per-thread allocation reporting is off");

        var keyPair = keyPairService.generateKeyPair();
        try (var packageFile = givenPackageOfSize(PACKAGE_SIZE)) {
            // Once through each first: class loading and the JIT's own allocations belong to nobody's
            // measurement, and they only happen the first time round.
            signByStreaming(packageFile, keyPair);
            integrityService.createSignatureFile(packageFile, keyPair).close();

            var streaming = allocatedBy(() -> signByStreaming(packageFile, keyPair));
            var readInOneGo = allocatedBy(() -> integrityService.createSignatureFile(packageFile, keyPair).close());

            System.out.printf(
                    "signing a %d byte package allocated %d bytes streaming, %d bytes read in one go (%.1fx)%n",
                    PACKAGE_SIZE,
                    streaming,
                    readInOneGo,
                    (double) streaming / readInOneGo);

            // The shipped path pays for the package and little else.
            assertTrue(
                    readInOneGo < PACKAGE_SIZE * 1.5,
                    "reading in one go allocated " + readInOneGo + " for a " + PACKAGE_SIZE + " byte package");
            // The streaming one pays for it several times over. Asserted well below the ~3.5x that the
            // doubling actually costs, so that this fails on a regression rather than on JVM noise.
            assertTrue(
                    streaming > PACKAGE_SIZE * 2.0,
                    "streaming allocated only " + streaming + " for a " + PACKAGE_SIZE + " byte package");
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ExtensionVersionIntegrityService extensionVersionIntegrityService(EntityManager entityManager) {
            return new ExtensionVersionIntegrityService(entityManager);
        }

        @Bean
        UUIDService uuidService() {
            return new UUIDService();
        }

        @Bean
        GenerateKeyPairJobService generateKeyPairJobService(
                EntityManager entityManager,
                RepositoryService repositoryService,
                UUIDService uuidService
        ) {
            return new GenerateKeyPairJobService(entityManager, repositoryService, uuidService);
        }
    }
}
