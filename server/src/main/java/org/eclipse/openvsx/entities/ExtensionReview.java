/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
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
package org.eclipse.openvsx.entities;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.eclipse.openvsx.json.ReviewJson;
import org.eclipse.openvsx.util.TimeUtil;

@Entity
public class ExtensionReview {

    @Id
    @GeneratedValue(generator = "extensionReviewSeq")
    @SequenceGenerator(name = "extensionReviewSeq", sequenceName = "extension_review_seq")
    private long id;

    @ManyToOne
    private Extension extension;

    private boolean active;

    private LocalDateTime timestamp;

    @ManyToOne
    private UserData user;

    private String title;

    @Column(length = 2048)
    private String comment;

    private int rating;

    /**
     * Convert to a JSON object.
     */
    public ReviewJson toReviewJson() {
        var json = new ReviewJson();
        json.setTimestamp(TimeUtil.toUTCString(this.getTimestamp()));
        json.setUser(this.getUser().toUserJson());
        json.setTitle(this.getTitle());
        json.setComment(this.getComment());
        json.setRating(this.getRating());
        return json;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Extension getExtension() {
        return extension;
    }

    public void setExtension(Extension extension) {
        this.extension = extension;
    }

    public UserData getUser() {
        return user;
    }

    public void setUser(UserData user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

}
