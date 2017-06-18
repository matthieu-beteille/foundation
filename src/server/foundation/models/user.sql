-- name: create-users!
-- Create users table
CREATE TABLE IF NOT EXISTS `user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `description` text,
  PRIMARY KEY (`id`)
);

-- name: create-user<!
INSERT INTO user (username, description) VALUES (:username, :description)

-- name: get-users-by-username
SELECT *
FROM user
WHERE username  = :username
