/** ******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.publish;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.bouncycastle.openssl.PEMParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.SignatureKeyPair;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TempFile;

import static org.eclipse.openvsx.entities.SignatureKeyPair.KEYPAIR_MODE_CREATE;
import static org.eclipse.openvsx.entities.SignatureKeyPair.KEYPAIR_MODE_RENEW;

@Component
public class ExtensionVersionIntegrityService {

    protected final Logger logger = LoggerFactory.getLogger(ExtensionVersionIntegrityService.class);

    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;

    @Value("${ovsx.integrity.key-pair:}")
    String keyPairMode;

    public ExtensionVersionIntegrityService(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.jsonMapper = JsonMapper.shared();
    }

    public boolean isEnabled() {
        return keyPairMode.equals(KEYPAIR_MODE_CREATE) || keyPairMode.equals(KEYPAIR_MODE_RENEW);
    }

    public boolean verifyExtensionVersion(TempFile extensionFile, TempFile signatureFile, TempFile publicKeyFile) {
        AsymmetricKeyParameter publicKeyParameters;
        try (var inReader = new InputStreamReader(Files.newInputStream(publicKeyFile.getPath()))) {
            var pemParser = new PEMParser(inReader);
            var publicKeyInfo = (SubjectPublicKeyInfo) pemParser.readObject();
            publicKeyParameters = PublicKeyFactory.createKey(publicKeyInfo);
        } catch (IOException e) {
            throw new ErrorResultException("Failed to read private key file", e);
        }

        // The PEM above can carry any key type; Ed25519Signer would have thrown a ClassCastException from
        // inside init() for anything else, which says nothing useful about the file that was handed in.
        if (!(publicKeyParameters instanceof Ed25519PublicKeyParameters ed25519PublicKey)) {
            throw new ErrorResultException("Public key file does not hold an Ed25519 public key");
        }

        boolean verified;
        try {
            // One array rather than a read loop into Ed25519Signer, for the reason spelled out on
            // createSignatureFile below: the streaming signer buffers the whole message anyway, and does
            // it in a structure that doubles as it grows.
            var message = Files.readAllBytes(extensionFile.getPath());
            var signature = Files.readAllBytes(signatureFile.getPath());
            verified = ed25519PublicKey
                    .verify(Ed25519.Algorithm.Ed25519, null, message, 0, message.length, signature, 0);
        } catch (IOException e) {
            throw new ErrorResultException("Failed to verify extension file", e);
        }

        return verified;
    }

    @Transactional
    public void setSignatureKeyPair(ExtensionVersion extVersion, SignatureKeyPair keyPair) {
        // find-then-set rather than merge: merge writes every column of the detached copy, so
        // anything that changed on the row since it was loaded gets reverted. Only the field
        // below is this method's to change - see #989.
        var managedVersion = entityManager.find(ExtensionVersion.class, extVersion.getId());
        if (managedVersion == null) {
            return;
        }
        managedVersion.setSignatureKeyPair(keyPair);
    }

    public TempFile generateSignature(TempFile extensionFile, SignatureKeyPair keyPair) throws IOException {
        var download = extensionFile.getResource();
        var resource = new FileResource();
        resource.setExtension(download.getExtension());
        resource.setName(NamingUtil.toFileFormat(download.getExtension(), ".sigzip"));
        resource.setType(FileResource.DOWNLOAD_SIG);
        var sigzipFile = new TempFile("signature", ".sigzip");
        sigzipFile.setResource(resource);
        try (var out = Files.newOutputStream(sigzipFile.getPath())) {
            try (var zip = new ZipOutputStream(out)) {
                var sigEntry = new ZipEntry(".signature.sig");
                zip.putNextEntry(sigEntry);
                try (var signatureFile = createSignatureFile(extensionFile, keyPair)) {
                    writeZipEntry(signatureFile, zip);
                }
                zip.closeEntry();

                var manifestEntry = new ZipEntry(".signature.manifest");
                zip.putNextEntry(manifestEntry);
                try (var manifestFile = generateSignatureManifest(extensionFile)) {
                    writeZipEntry(manifestFile, zip);
                }
                zip.closeEntry();

                // Add dummy file to the archive because VS Code checks if it exists
                var dummyEntry = new ZipEntry(".signature.p7s");
                zip.putNextEntry(dummyEntry);
                zip.write(new byte[0]);
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new ErrorResultException("Failed to sign extension file", e);
        }

        return sigzipFile;
    }

    /**
     * Signs the package with the key pair's private key.
     * <p>
     * Reads it into one array rather than streaming it through {@link Ed25519Signer}, which reads as the
     * memory-safe option and is the opposite of one. Pure Ed25519 (RFC 8032) hashes the message twice -
     * once for the nonce, once for the challenge - so a signer cannot discard what it has been fed, and
     * BouncyCastle's keeps it in a {@code ByteArrayOutputStream} that doubles its capacity as it fills.
     * Feeding that a 300 MB package peaks at three quarters of a gigabyte, because the 256 MB array and
     * the 512 MB one being copied into are both live during the last growth - which is why publishing
     * one used to need a 2 GB heap (see #1450). Read in one go, the cost is the size of the package.
     * <p>
     * It is still the size of the package, so an instance running with the integrity service enabled
     * wants a heap comfortably above {@code ovsx.publishing.max-content-size}. Constant memory is not
     * reachable from here: every {@code sign} overload BouncyCastle exposes for pure Ed25519 takes a
     * {@code byte[]}, and its one streaming signer implements Ed25519ph, whose signatures are a
     * different scheme that nothing verifying these packages today would accept.
     * <p>
     * Package-private so that a test can measure what it allocates against the streaming alternative.
     */
    TempFile createSignatureFile(TempFile extensionFile, SignatureKeyPair keyPair) throws IOException {
        var privateKeyParameters = new Ed25519PrivateKeyParameters(keyPair.getPrivateKey(), 0);
        var message = Files.readAllBytes(extensionFile.getPath());
        var signature = new byte[Ed25519PrivateKeyParameters.SIGNATURE_SIZE];
        privateKeyParameters.sign(Ed25519.Algorithm.Ed25519, null, message, 0, message.length, signature, 0);

        var signatureFile = new TempFile("signature", ".sig");
        Files.write(signatureFile.getPath(), signature);
        return signatureFile;
    }

    private TempFile generateSignatureManifest(TempFile extensionFile) throws IOException {
        var base64 = new Base64();
        var manifestEntries = jsonMapper.createObjectNode();
        try (var zip = new ZipFile(extensionFile.getPath().toFile())) {
            var iterator = zip.stream().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.isDirectory()) {
                    continue;
                }

                try (var entryStream = zip.getInputStream(entry)) {
                    var manifestEntry = generateManifestEntry(entryStream, entry.getSize(), base64);
                    manifestEntries.set(
                            new String(base64.encode(entry.getName().getBytes(StandardCharsets.UTF_8))),
                            manifestEntry);
                }
            }
        }

        var manifest = jsonMapper.createObjectNode();
        try (var extensionStream = Files.newInputStream(extensionFile.getPath())) {
            manifest.set(
                    "package",
                    generateManifestEntry(extensionStream, Files.size(extensionFile.getPath()), base64));
        }
        manifest.set("entries", manifestEntries);

        var manifestFile = new TempFile("signature", ".manifest");
        jsonMapper.writeValue(manifestFile.getPath().toFile(), manifest);
        return manifestFile;
    }

    private JsonNode generateManifestEntry(InputStream stream, long size, Base64 base64) throws IOException {
        var manifestEntry = jsonMapper.createObjectNode();
        manifestEntry.put("size", size);

        var manifestEntryDigests = jsonMapper.createObjectNode();
        var sha256 = new String(base64.encode(DigestUtils.sha256(stream)));
        manifestEntryDigests.put("sha256", sha256);
        manifestEntry.set("digests", manifestEntryDigests);
        return manifestEntry;
    }

    private void writeZipEntry(TempFile file, ZipOutputStream out) throws IOException {
        try (var in = Files.newInputStream(file.getPath())) {
            int length;
            var buffer = new byte[1024];
            while ((length = in.read(buffer)) >= 0) {
                out.write(buffer, 0, length);
            }
        }
    }
}
