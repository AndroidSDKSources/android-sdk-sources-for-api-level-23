/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.util.jar;

import org.apache.harmony.security.utils.JarUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import libcore.io.Base64;

/**
 * Non-public class used by {@link JarFile} and {@link JarInputStream} to manage
 * the verification of signed JARs. {@code JarFile} and {@code JarInputStream}
 * objects are expected to have a {@code JarVerifier} instance member which
 * can be used to carry out the tasks associated with verifying a signed JAR.
 * These tasks would typically include:
 * <ul>
 * <li>verification of all signed signature files
 * <li>confirmation that all signed data was signed only by the party or parties
 * specified in the signature block data
 * <li>verification that the contents of all signature files (i.e. {@code .SF}
 * files) agree with the JAR entries information found in the JAR manifest.
 * </ul>
 */
class JarVerifier {
    /**
     * List of accepted digest algorithms. This list is in order from most
     * preferred to least preferred.
     */
    private static final String[] DIGEST_ALGORITHMS = new String[] {
        "SHA-512",
        "SHA-384",
        "SHA-256",
        "SHA1",
    };

    private final String jarName;
    private final Manifest manifest;
    private final HashMap<String, byte[]> metaEntries;
    private final int mainAttributesEnd;

    private final Hashtable<String, HashMap<String, Attributes>> signatures =
            new Hashtable<String, HashMap<String, Attributes>>(5);

    private final Hashtable<String, Certificate[]> certificates =
            new Hashtable<String, Certificate[]>(5);

    private final Hashtable<String, Certificate[][]> verifiedEntries =
            new Hashtable<String, Certificate[][]>();

    /**
     * Stores and a hash and a message digest and verifies that massage digest
     * matches the hash.
     */
    static class VerifierEntry extends OutputStream {

        private final String name;

        private final MessageDigest digest;

        private final byte[] hash;

        private final Certificate[][] certChains;

        private final Hashtable<String, Certificate[][]> verifiedEntries;

        VerifierEntry(String name, MessageDigest digest, byte[] hash,
                Certificate[][] certChains, Hashtable<String, Certificate[][]> verifedEntries) {
            this.name = name;
            this.digest = digest;
            this.hash = hash;
            this.certChains = certChains;
            this.verifiedEntries = verifedEntries;
        }

        /**
         * Updates a digest with one byte.
         */
        @Override
        public void write(int value) {
            digest.update((byte) value);
        }

        /**
         * Updates a digest with byte array.
         */
        @Override
        public void write(byte[] buf, int off, int nbytes) {
            digest.update(buf, off, nbytes);
        }

        /**
         * Verifies that the digests stored in the manifest match the decrypted
         * digests from the .SF file. This indicates the validity of the
         * signing, not the integrity of the file, as its digest must be
         * calculated and verified when its contents are read.
         *
         * @throws SecurityException
         *             if the digest value stored in the manifest does <i>not</i>
         *             agree with the decrypted digest as recovered from the
         *             <code>.SF</code> file.
         */
        void verify() {
            byte[] d = digest.digest();
            if (!MessageDigest.isEqual(d, Base64.decode(hash))) {
                throw invalidDigest(JarFile.MANIFEST_NAME, name, name);
            }
            verifiedEntries.put(name, certChains);
        }
    }

    private static SecurityException invalidDigest(String signatureFile, String name,
            String jarName) {
        throw new SecurityException(signatureFile + " has invalid digest for " + name +
                " in " + jarName);
    }

    private static SecurityException failedVerification(String jarName, String signatureFile) {
        throw new SecurityException(jarName + " failed verification of " + signatureFile);
    }

    /**
     * Constructs and returns a new instance of {@code JarVerifier}.
     *
     * @param name
     *            the name of the JAR file being verified.
     */
    JarVerifier(String name, Manifest manifest, HashMap<String, byte[]> metaEntries) {
        jarName = name;
        this.manifest = manifest;
        this.metaEntries = metaEntries;
        this.mainAttributesEnd = manifest.getMainAttributesEnd();
    }

    /**
     * Invoked for each new JAR entry read operation from the input
     * stream. This method constructs and returns a new {@link VerifierEntry}
     * which contains the certificates used to sign the entry and its hash value
     * as specified in the JAR MANIFEST format.
     *
     * @param name
     *            the name of an entry in a JAR file which is <b>not</b> in the
     *            {@code META-INF} directory.
     * @return a new instance of {@link VerifierEntry} which can be used by
     *         callers as an {@link OutputStream}.
     */
    VerifierEntry initEntry(String name) {
        // If no manifest is present by the time an entry is found,
        // verification cannot occur. If no signature files have
        // been found, do not verify.
        if (manifest == null || signatures.isEmpty()) {
            return null;
        }

        Attributes attributes = manifest.getAttributes(name);
        // entry has no digest
        if (attributes == null) {
            return null;
        }

        ArrayList<Certificate[]> certChains = new ArrayList<Certificate[]>();
        Iterator<Map.Entry<String, HashMap<String, Attributes>>> it = signatures.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, HashMap<String, Attributes>> entry = it.next();
            HashMap<String, Attributes> hm = entry.getValue();
            if (hm.get(name) != null) {
                // Found an entry for entry name in .SF file
                String signatureFile = entry.getKey();
                Certificate[] certChain = certificates.get(signatureFile);
                if (certChain != null) {
                    certChains.add(certChain);
                }
            }
        }

        // entry is not signed
        if (certChains.isEmpty()) {
            return null;
        }
        Certificate[][] certChainsArray = certChains.toArray(new Certificate[certChains.size()][]);

        for (int i = 0; i < DIGEST_ALGORITHMS.length; i++) {
            final String algorithm = DIGEST_ALGORITHMS[i];
            final String hash = attributes.getValue(algorithm + "-Digest");
            if (hash == null) {
                continue;
            }
            byte[] hashBytes = hash.getBytes(StandardCharsets.ISO_8859_1);

            try {
                return new VerifierEntry(name, MessageDigest.getInstance(algorithm), hashBytes,
                        certChainsArray, verifiedEntries);
            } catch (NoSuchAlgorithmException ignored) {
            }
        }
        return null;
    }

    /**
     * Add a new meta entry to the internal collection of data held on each JAR
     * entry in the {@code META-INF} directory including the manifest
     * file itself. Files associated with the signing of a JAR would also be
     * added to this collection.
     *
     * @param name
     *            the name of the file located in the {@code META-INF}
     *            directory.
     * @param buf
     *            the file bytes for the file called {@code name}.
     * @see #removeMetaEntries()
     */
    void addMetaEntry(String name, byte[] buf) {
        metaEntries.put(name.toUpperCase(Locale.US), buf);
    }

    /**
     * If the associated JAR file is signed, check on the validity of all of the
     * known signatures.
     *
     * @return {@code true} if the associated JAR is signed and an internal
     *         check verifies the validity of the signature(s). {@code false} if
     *         the associated JAR file has no entries at all in its {@code
     *         META-INF} directory. This situation is indicative of an invalid
     *         JAR file.
     *         <p>
     *         Will also return {@code true} if the JAR file is <i>not</i>
     *         signed.
     * @throws SecurityException
     *             if the JAR file is signed and it is determined that a
     *             signature block file contains an invalid signature for the
     *             corresponding signature file.
     */
    synchronized boolean readCertificates() {
        if (metaEntries.isEmpty()) {
            return false;
        }

        Iterator<String> it = metaEntries.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (key.endsWith(".DSA") || key.endsWith(".RSA") || key.endsWith(".EC")) {
                verifyCertificate(key);
                it.remove();
            }
        }
        return true;
    }

    /**
     * @param certFile
     */
    private void verifyCertificate(String certFile) {
        // Found Digital Sig, .SF should already have been read
        String signatureFile = certFile.substring(0, certFile.lastIndexOf('.')) + ".SF";
        byte[] sfBytes = metaEntries.get(signatureFile);
        if (sfBytes == null) {
            return;
        }

        byte[] manifestBytes = metaEntries.get(JarFile.MANIFEST_NAME);
        // Manifest entry is required for any verifications.
        if (manifestBytes == null) {
            return;
        }

        byte[] sBlockBytes = metaEntries.get(certFile);
        try {
            Certificate[] signerCertChain = JarUtils.verifySignature(
                    new ByteArrayInputStream(sfBytes),
                    new ByteArrayInputStream(sBlockBytes));
            if (signerCertChain != null) {
                certificates.put(signatureFile, signerCertChain);
            }
        } catch (IOException e) {
            return;
        } catch (GeneralSecurityException e) {
            throw failedVerification(jarName, signatureFile);
        }

        // Verify manifest hash in .sf file
        Attributes attributes = new Attributes();
        HashMap<String, Attributes> entries = new HashMap<String, Attributes>();
        try {
            ManifestReader im = new ManifestReader(sfBytes, attributes);
            im.readEntries(entries, null);
        } catch (IOException e) {
            return;
        }

        // Do we actually have any signatures to look at?
        if (attributes.get(Attributes.Name.SIGNATURE_VERSION) == null) {
            return;
        }

        boolean createdBySigntool = false;
        String createdBy = attributes.getValue("Created-By");
        if (createdBy != null) {
            createdBySigntool = createdBy.indexOf("signtool") != -1;
        }

        // Use .SF to verify the mainAttributes of the manifest
        // If there is no -Digest-Manifest-Main-Attributes entry in .SF
        // file, such as those created before java 1.5, then we ignore
        // such verification.
        if (mainAttributesEnd > 0 && !createdBySigntool) {
            String digestAttribute = "-Digest-Manifest-Main-Attributes";
            if (!verify(attributes, digestAttribute, manifestBytes, 0, mainAttributesEnd, false, true)) {
                throw failedVerification(jarName, signatureFile);
            }
        }

        // Use .SF to verify the whole manifest.
        String digestAttribute = createdBySigntool ? "-Digest" : "-Digest-Manifest";
        if (!verify(attributes, digestAttribute, manifestBytes, 0, manifestBytes.length, false, false)) {
            Iterator<Map.Entry<String, Attributes>> it = entries.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Attributes> entry = it.next();
                Manifest.Chunk chunk = manifest.getChunk(entry.getKey());
                if (chunk == null) {
                    return;
                }
                if (!verify(entry.getValue(), "-Digest", manifestBytes,
                        chunk.start, chunk.end, createdBySigntool, false)) {
                    throw invalidDigest(signatureFile, entry.getKey(), jarName);
                }
            }
        }
        metaEntries.put(signatureFile, null);
        signatures.put(signatureFile, entries);
    }

    /**
     * Returns a <code>boolean</code> indication of whether or not the
     * associated jar file is signed.
     *
     * @return {@code true} if the JAR is signed, {@code false}
     *         otherwise.
     */
    boolean isSignedJar() {
        return certificates.size() > 0;
    }

    private boolean verify(Attributes attributes, String entry, byte[] data,
            int start, int end, boolean ignoreSecondEndline, boolean ignorable) {
        for (int i = 0; i < DIGEST_ALGORITHMS.length; i++) {
            String algorithm = DIGEST_ALGORITHMS[i];
            String hash = attributes.getValue(algorithm + entry);
            if (hash == null) {
                continue;
            }

            MessageDigest md;
            try {
                md = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                continue;
            }
            if (ignoreSecondEndline && data[end - 1] == '\n' && data[end - 2] == '\n') {
                md.update(data, start, end - 1 - start);
            } else {
                md.update(data, start, end - start);
            }
            byte[] b = md.digest();
            byte[] hashBytes = hash.getBytes(StandardCharsets.ISO_8859_1);
            return MessageDigest.isEqual(b, Base64.decode(hashBytes));
        }
        return ignorable;
    }

    /**
     * Returns all of the {@link java.security.cert.Certificate} chains that
     * were used to verify the signature on the JAR entry called
     * {@code name}. Callers must not modify the returned arrays.
     *
     * @param name
     *            the name of a JAR entry.
     * @return an array of {@link java.security.cert.Certificate} chains.
     */
    Certificate[][] getCertificateChains(String name) {
        return verifiedEntries.get(name);
    }

    /**
     * Remove all entries from the internal collection of data held about each
     * JAR entry in the {@code META-INF} directory.
     */
    void removeMetaEntries() {
        metaEntries.clear();
    }
}
