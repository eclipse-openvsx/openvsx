-- Close a race in postReview(): concurrent duplicate-review checks under READ COMMITTED could each
-- pass before either insert committed, letting one user hold more than one active review for the
-- same extension. Drop all but the most recent such review per (extension, user) before the unique
-- index below, since a partial unique index cannot be created over existing duplicates.
--
-- Rank by timestamp, not id: ExtensionReview's sequence generator has no explicit allocationSize, so
-- JPA defaults it to 50, matching this sequence's INCREMENT 50 - a pooled allocator, where separate
-- application instances hold different id blocks and a later insert can end up with a lower id than
-- an earlier one. id is only the tie-breaker for identical (or missing) timestamps.
DELETE FROM extension_review r
WHERE r.active
AND r.id NOT IN (
    SELECT DISTINCT ON (extension_id, user_id) id
    FROM extension_review
    WHERE active
    ORDER BY extension_id, user_id, timestamp DESC NULLS LAST, id DESC
);

UPDATE extension e
SET review_count = r.reviews,
    average_rating = r.rating
FROM (
    SELECT extension_id, COUNT(id) reviews, AVG(rating) rating
    FROM extension_review
    WHERE active = TRUE
    GROUP BY extension_id
) r
WHERE e.id = r.extension_id;

CREATE UNIQUE INDEX unique_active_review ON extension_review(extension_id, user_id) WHERE active;
