/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.eclipse;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * https://eclipsefdn.github.io/openvsx-publisher-agreement-specs/#/paths/~1publisher_agreement/post
 */
class PublisherAgreementResponse {

    /** Unique identifier for an addressable object in the API. */
    @JsonProperty("PersonID")
    String personID;

    /** Unique identifier for an addressable object in the API. */
    @JsonProperty("DocumentID")
    String documentID;

    /** The version number for the current document. */
    @JsonProperty("Version")
    String version;

    /** Date string in the RFC 3339 format. */
    @JsonProperty("EffectiveDate")
    String effectiveDate;

    /** Date string in the RFC 3339 format. */
    @JsonProperty("ReceivedDate")
    String receivedDate;

    /** The signed document as a blob entity. */
    @JsonProperty("ScannedDocumentBLOB")
    String scannedDocumentBLOB;

    /** The MIME type for the posted document blob. */
    @JsonProperty("ScannedDocumentMime")
    String scannedDocumentMime;

    /** The name of the document being posted. */
    @JsonProperty("ScannedDocumentFileName")
    String scannedDocumentFileName;

    /** Comment about the document being posted. */
    @JsonProperty("Comments")
    String comments;
}
