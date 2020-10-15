CREATE DATABASE  IF NOT EXISTS `salattest` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'salattest' IDENTIFIED BY 'salattest';
GRANT ALL privileges ON `salattest`.* TO `salattest`@`%`;
