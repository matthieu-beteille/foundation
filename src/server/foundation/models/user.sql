-- name: create-users!
-- Create users table
CREATE TABLE IF NOT EXISTS `user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `address_id` int,
  `description` text,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`address_id`) REFERENCES address(`id`)
);

-- name: create-user<!
INSERT INTO user (username, description, address_id) VALUES (:username, :description, :address)

-- name: create-addr<!
INSERT INTO address (postcode) VALUES (:postcode)

-- name: get-users-by-username
SELECT *
FROM user
WHERE username  = :username

-- name: get-user-address
SELECT address.*
FROM user
RIGHT OUTER JOIN address ON user.address_id = address.id
WHERE user.id = :id;

-- name: create-address!
CREATE TABLE IF NOT EXISTS `address` (
  `id` int NOT NULL AUTO_INCREMENT,
  `postcode` varchar(100) NOT NULL,
  PRIMARY KEY (`id`)
);

-- name: test
SELECT :fields FROM :table
WHERE :field = :field_value
