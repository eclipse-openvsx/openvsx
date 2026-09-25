-- Close a race in postReview(): concurrent duplicate-review checks under READ COMMITTED could each
-- pass before either insert committed, letting one user hold more than one active review for the
-- same extension. Keep the oldest such review per (extension, user) and deactivate the rest before
-- the unique index below, since a partial unique index cannot be created over existing duplicates.
UPDATE extension_review r
SET active = false
WHERE r.active
AND r.id NOT IN (
    SELECT MIN(id)
    FROM extension_review
    WHERE active
    GROUP BY extension_id, user_id
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
