-- AI tidy's daily title allowance per user, kept across restarts.
ALTER TABLE users ADD COLUMN tidy_title_day   DATE;
ALTER TABLE users ADD COLUMN tidy_titles_used INT NOT NULL DEFAULT 0;
