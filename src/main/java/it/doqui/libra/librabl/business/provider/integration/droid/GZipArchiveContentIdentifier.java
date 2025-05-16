/**
 * Copyright (c) 2012, The National Archives <pronom@nationalarchives.gsi.gov.uk>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following
 * conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of the The National Archives nor the
 *    names of its contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package it.doqui.libra.librabl.business.provider.integration.droid;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipUtils;
import uk.gov.nationalarchives.droid.container.ContainerSignatureDefinitions;
import uk.gov.nationalarchives.droid.core.BinarySignatureIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.RequestIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.resource.GZipIdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.resource.RequestMetaData;

import java.io.File;
import java.io.IOException;
import java.net.URI;

@Slf4j
public class GZipArchiveContentIdentifier {

    private static final long SIZE = 12L;
    private static final long TIME = 13L;

    private BinarySignatureIdentifier binarySignatureIdentifier;
    private ContainerSignatureDefinitions containerSignatureDefinitions;
    private String path;
    private String slash;
    private String slash1;
    private File tmpDir;

    /**
     *
     * @param binarySignatureIdentifier     binary signature identifier
     * @param containerSignatureDefinitions container signatures
     * @param path                          current archive path
     * @param slash                         local path element delimiter
     * @param slash1                        local first container prefix delimiter
     */
    public GZipArchiveContentIdentifier(final BinarySignatureIdentifier binarySignatureIdentifier,
                                        final ContainerSignatureDefinitions containerSignatureDefinitions,
                                        final String path, final String slash, final String slash1) {

        synchronized (this) {
            this.binarySignatureIdentifier = binarySignatureIdentifier;
            this.containerSignatureDefinitions = containerSignatureDefinitions;
            this.path = path;
            this.slash = slash;
            this.slash1 = slash1;
            if (tmpDir == null) {
                tmpDir = new File(System.getProperty("java.io.tmpdir"));
            }
        }
    }

    public final void identify(final URI uri, final IdentificationRequest request) {

        final String newPath = "gzip:" + slash1 + path + request.getFileName() + "!" + slash;
        slash1 = "";
        final URI newUri = URI.create(GzipUtils.getUncompressedFilename(uri.toString()));

        final RequestIdentifier identifier = new RequestIdentifier(newUri);
        final RequestMetaData metaData = new RequestMetaData(SIZE, TIME, uri.getPath());
        try (var gzRequest = new GZipIdentificationRequest(metaData, identifier, tmpDir.toPath())) {
            try (var gzin = new GzipCompressorInputStream(request.getSourceInputStream())) {
                gzRequest.open(gzin);
                final var gzResults = binarySignatureIdentifier.matchBinarySignatures(gzRequest);

                //TODO
//            final ResultPrinter resultPrinter = new ResultPrinter(binarySignatureIdentifier,
//                containerSignatureDefinitions, newPath, slash, slash1, true);
//            resultPrinter.print(gzResults, gzRequest);
            } catch (IOException ioe) {
                log.error(String.format("Exception %s (%s)", ioe.getMessage(), newPath), ioe); // continue after corrupt archive
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
