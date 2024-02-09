-- CREATE DATABASE  IF NOT EXISTS `salat` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE  IF NOT EXISTS `salat` CHARACTER SET utf8 COLLATE utf8_bin;
CREATE USER IF NOT EXISTS 'salattest' IDENTIFIED BY 'salattest';
-- GRANT USAGE ON 'salat'.* TO 'salattest'@'';
GRANT ALL privileges ON `salat`.* TO `salattest`@`%`;
USE `salat`;
-- MySQL dump 10.13  Distrib 5.6.17, for Win32 (x86)
--
-- Host: database01    Database: salat
-- ------------------------------------------------------
-- Server version	5.5.54-0ubuntu0.14.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `customer`
--

DROP TABLE IF EXISTS `customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `customer` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `address` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `lastupdate` datetime DEFAULT NULL,
  `lastupdatedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `name` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `shortname` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `updatecounter` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=181389 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `customer`
--

LOCK TABLES `customer` WRITE;
/*!40000 ALTER TABLE `customer` DISABLE KEYS */;
INSERT INTO `customer` VALUES (3201,'Stadthausbrücke 3,\r\n20355 Hamburg','2006-11-24 14:19:01','mm','2007-04-05 13:05:34','mm','Hamburger Berater Team GmbH','HBT',4),(181388,'Irgendwo, wo keiner hin will','2019-09-11 04:49:21','bm',NULL,NULL,'Mega Corp AG','Mega Corp',NULL);
/*!40000 ALTER TABLE `customer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `customerorder`
--

DROP TABLE IF EXISTS `customerorder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `customerorder` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `currency` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `debithours` double DEFAULT NULL,
  `debithoursunit` tinyint(4) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `fromDate` date DEFAULT NULL,
  `hide` bit(1) DEFAULT NULL,
  `hourly_rate` double DEFAULT NULL,
  `lastupdate` datetime DEFAULT NULL,
  `lastupdatedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `order_customer` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `responsible_customer_contractually` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `responsible_customer_technical` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `shortdescription` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `sign` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `statusreport` int(11) DEFAULT NULL,
  `untilDate` date DEFAULT NULL,
  `updatecounter` int(11) DEFAULT NULL,
  `CUSTOMER_ID` bigint(20) DEFAULT NULL,
  `RESPONSIBLE_HBT_CONTRACTUALLY_ID` bigint(20) DEFAULT NULL,
  `RESPONSIBLE_HBT_ID` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKB0BA72106B60A2F3` (`RESPONSIBLE_HBT_CONTRACTUALLY_ID`),
  KEY `FKB0BA7210F5620BCF` (`RESPONSIBLE_HBT_ID`),
  KEY `FKB0BA721010D8DFF2` (`CUSTOMER_ID`),
  CONSTRAINT `FKB0BA721010D8DFF2` FOREIGN KEY (`CUSTOMER_ID`) REFERENCES `customer` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKB0BA72106B60A2F3` FOREIGN KEY (`RESPONSIBLE_HBT_CONTRACTUALLY_ID`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKB0BA7210F5620BCF` FOREIGN KEY (`RESPONSIBLE_HBT_ID`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=180970 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `customerorder`
--

LOCK TABLES `customerorder` WRITE;
/*!40000 ALTER TABLE `customerorder` DISABLE KEYS */;
INSERT INTO `customerorder` VALUES
                                (5190,'2006-12-05 13:35:24','mm','EUR',NULL,NULL,'Urlaub','2007-01-01','\0',0,'2007-07-16 12:44:37','adm','Urlaub','---','---','Urlaub','URLAUB',0,NULL,7,3201,1680,1680),
                                (5191,'2006-12-05 13:36:31','mm','EUR',NULL,NULL,'Krankheit','2007-01-01','\0',0,'2007-07-16 12:44:09','adm','Krankheit','---','---','Krankheit','KRANK',0,NULL,7,3201,1680,1680),
                                (180969,'2019-09-11 04:51:49','bm','EUR',NULL,NULL,'Rumsitzen','2019-09-11','\0',100,NULL,NULL,'Ergonomische Studie','Dörte Dörtersen','Hans Hansmann','Rumsitzen','111',0,NULL,NULL,181388,178367,178367),
                                (180970,'2019-09-11 04:51:49','bm','EUR',NULL,NULL,'Fortbildung','2019-09-11','\0',100,NULL,NULL,'Fortbildung','Dörte Dörtersen','Hans Hansmann','Fortbildung','i976',0,NULL,NULL,3201,1680,1680);
/*!40000 ALTER TABLE `customerorder` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `employee`
--

DROP TABLE IF EXISTS `employee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `employee` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `firstname` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `gender` char(1) COLLATE utf8_bin NOT NULL,
  `lastname` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `lastupdate` datetime DEFAULT NULL,
  `lastupdatedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `loginname` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `password` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `passwordchange` bit(1) DEFAULT NULL,
  `sign` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `status` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `updatecounter` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=178369 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `employee`
--

LOCK TABLES `employee` WRITE;
/*!40000 ALTER TABLE `employee` DISABLE KEYS */;
INSERT INTO `employee` VALUES (1680,'2006-12-27 13:24:28','adm','salat','m','admin','2019-09-11 04:47:09','adm','admin','$2a$10$WUeKyLBkvFBOFKPcj4KTduQe9YRyRrAffzGvinGzx77zkdstiVtF.','\0','adm','adm',151),(178367,'2019-09-11 04:43:54','adm','Bossy','f','Bossmann','2019-09-11 04:58:16','bm','bm','084243855820f9ca47f466f645784636','\0','bm','adm',5),(178368,'2019-09-11 04:48:00','bm','Testy','m','Testmann','2019-09-11 04:57:39','tt','tt','accc9105df5383111407fd5b41255e23','\0','tt','ma',2);
/*!40000 ALTER TABLE `employee` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `employeecontract`
--

DROP TABLE IF EXISTS `employeecontract`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `employeecontract` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `dailyWorkingTime` double DEFAULT NULL,
  `fixedUntil` date DEFAULT NULL,
  `freelancer` bit(1) DEFAULT NULL,
  `hide` bit(1) DEFAULT NULL,
  `lastupdate` datetime DEFAULT NULL,
  `lastupdatedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `overtimeStatic` double NOT NULL,
  `reportAcceptanceDate` date DEFAULT NULL,
  `reportReleaseDate` date DEFAULT NULL,
  `taskDescription` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `updatecounter` int(11) DEFAULT NULL,
  `useOvertimeOld` bit(1) DEFAULT NULL,
  `validFrom` date DEFAULT NULL,
  `validUntil` date DEFAULT NULL,
  `EMPLOYEE_ID` bigint(20) DEFAULT NULL,
  `SUPERVISOR_ID` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKF359ADE030CF4EF8` (`SUPERVISOR_ID`),
  KEY `FKF359ADE0D3F5ADF2` (`EMPLOYEE_ID`),
  CONSTRAINT `FKF359ADE030CF4EF8` FOREIGN KEY (`SUPERVISOR_ID`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKF359ADE0D3F5ADF2` FOREIGN KEY (`EMPLOYEE_ID`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=180663 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `employeecontract`
--

LOCK TABLES `employeecontract` WRITE;
/*!40000 ALTER TABLE `employeecontract` DISABLE KEYS */;
INSERT INTO `employeecontract` VALUES (5970,'2006-12-28 13:32:40','adm',0,NULL,'\0','\0','2014-11-25 16:54:24','adm',0,'2006-01-01','2011-08-30','technischer Benutzer',15,'\0','2006-01-01',NULL,1680,1680),(180661,'2019-09-11 04:47:04','adm',8,NULL,'\0','\0','2019-09-11 04:47:22','system',0,'2019-01-01','2019-01-01','Sinnlose Aufträge verteilen',2,'\0','2019-01-01',NULL,178367,NULL),(180662,'2019-09-11 04:54:21','bm',8,NULL,'\0','\0','2019-09-11 04:58:12','bm',0,'2019-01-01','2019-01-01','Entwickler, Berater',3,'\0','2019-09-11',NULL,178368,178367);
/*!40000 ALTER TABLE `employeecontract` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `employeeorder`
--

DROP TABLE IF EXISTS `employeeorder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `employeeorder` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `debithours` double DEFAULT NULL,
  `debithoursunit` tinyint(4) DEFAULT NULL,
  `fromDate` date DEFAULT NULL,
  `lastupdate` datetime DEFAULT NULL,
  `lastupdatedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `sign` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `untilDate` date DEFAULT NULL,
  `updatecounter` int(11) DEFAULT NULL,
  `EMPLOYEEORDERCONTENT_ID` bigint(20) DEFAULT NULL,
  `EMPLOYEECONTRACT_ID` bigint(20) DEFAULT NULL,
  `SUBORDER_ID` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKB798CD40789484B2` (`EMPLOYEECONTRACT_ID`),
  KEY `FKB798CD406F5511F2` (`SUBORDER_ID`),
  KEY `FKB798CD408F771E12` (`EMPLOYEEORDERCONTENT_ID`),
  CONSTRAINT `FKB798CD406F5511F2` FOREIGN KEY (`SUBORDER_ID`) REFERENCES `suborder` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKB798CD40789484B2` FOREIGN KEY (`EMPLOYEECONTRACT_ID`) REFERENCES `employeecontract` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKB798CD408F771E12` FOREIGN KEY (`EMPLOYEEORDERCONTENT_ID`) REFERENCES `employeeordercontent` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=183212 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `employeeorder`
--

LOCK TABLES `employeeorder` WRITE;
/*!40000 ALTER TABLE `employeeorder` DISABLE KEYS */;
INSERT INTO `employeeorder` VALUES (183209,'2019-09-11 04:54:51','bm',NULL,NULL,'2019-09-11',NULL,NULL,'',NULL,NULL,NULL,180662,180692),
                                   (183210,'2019-09-11 04:55:00','system',240,0,'2019-09-11',NULL,NULL,' ',NULL,NULL,NULL,180662,60208),
                                   (183211,'2019-09-11 04:55:00','system',NULL,NULL,'2019-09-11',NULL,NULL,' ',NULL,NULL,NULL,180662,5861);
/*!40000 ALTER TABLE `employeeorder` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `employeeordercontent`
--

DROP TABLE IF EXISTS `employeeordercontent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `employeeordercontent` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `additional_risks` text COLLATE utf8_bin DEFAULT NULL,
  `arrangement` text COLLATE utf8_bin DEFAULT NULL,
  `boundary` text COLLATE utf8_bin DEFAULT NULL,
  `committed_emp` bit(1) DEFAULT NULL,
  `committed_mgmt` bit(1) DEFAULT NULL,
  `contact_contract_customer` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `contact_tech_customer` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `description` text COLLATE utf8_bin DEFAULT NULL,
  `lastupdate` datetime DEFAULT NULL,
  `lastupdatedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `procedure` text COLLATE utf8_bin DEFAULT NULL,
  `qm_process_id` int(11) DEFAULT NULL,
  `task` text COLLATE utf8_bin DEFAULT NULL,
  `updatecounter` int(11) DEFAULT NULL,
  `COMMITTEDBY_EMP` bigint(20) DEFAULT NULL,
  `COMMITTEDBY_MGMT` bigint(20) DEFAULT NULL,
  `CONTACT_CONTRACT_HBT` bigint(20) DEFAULT NULL,
  `CONTACT_TECH_HBT` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK42F0A3192144BD72` (`CONTACT_CONTRACT_HBT`),
  KEY `FK42F0A319C4D24476` (`CONTACT_TECH_HBT`),
  KEY `FK42F0A31939653` (`COMMITTEDBY_MGMT`),
  KEY `FK42F0A31966D8F502` (`COMMITTEDBY_EMP`),
  CONSTRAINT `FK42F0A3192144BD72` FOREIGN KEY (`CONTACT_CONTRACT_HBT`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK42F0A31939653` FOREIGN KEY (`COMMITTEDBY_MGMT`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK42F0A31966D8F502` FOREIGN KEY (`COMMITTEDBY_EMP`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK42F0A319C4D24476` FOREIGN KEY (`CONTACT_TECH_HBT`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=173502 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `employeeordercontent`
--

LOCK TABLES `employeeordercontent` WRITE;
/*!40000 ALTER TABLE `employeeordercontent` DISABLE KEYS */;
/*!40000 ALTER TABLE `employeeordercontent` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `invoice`
--

DROP TABLE IF EXISTS `invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `invoice` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `description` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `lastupdate` datetime DEFAULT NULL,
  `lastupdatedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `updatecounter` int(11) DEFAULT NULL,
  `CUSTOMER_ID` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKD80EDB0D10D8DFF2` (`CUSTOMER_ID`),
  CONSTRAINT `FKD80EDB0D10D8DFF2` FOREIGN KEY (`CUSTOMER_ID`) REFERENCES `customer` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `invoice`
--

LOCK TABLES `invoice` WRITE;
/*!40000 ALTER TABLE `invoice` DISABLE KEYS */;
/*!40000 ALTER TABLE `invoice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `overtime`
--

DROP TABLE IF EXISTS `overtime`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `overtime` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `comment` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `time` double DEFAULT NULL,
  `EMPLOYEECONTRACT_ID` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK237269C1789484B2` (`EMPLOYEECONTRACT_ID`),
  CONSTRAINT `FK237269C1789484B2` FOREIGN KEY (`EMPLOYEECONTRACT_ID`) REFERENCES `employeecontract` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=180261 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `overtime`
--

LOCK TABLES `overtime` WRITE;
/*!40000 ALTER TABLE `overtime` DISABLE KEYS */;
INSERT INTO `overtime` VALUES (180259,'initial overtime','2019-09-11 04:47:04','adm',0,180661),(180260,'initial overtime','2019-09-11 04:54:21','bm',0,180662);
/*!40000 ALTER TABLE `overtime` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `publicholiday`
--

DROP TABLE IF EXISTS `publicholiday`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `publicholiday` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `refdate` date DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=153764 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `publicholiday`
--

LOCK TABLES `publicholiday` WRITE;
/*!40000 ALTER TABLE `publicholiday` DISABLE KEYS */;
INSERT INTO `publicholiday` VALUES (3140,'Neujahr','2006-01-01'),(3141,'Karfreitag','2006-04-14'),(3142,'Ostersonntag','2006-04-16'),(3143,'Ostermontag','2006-04-17'),(3144,'Maifeiertag','2006-05-01'),(3145,'Christi Himmelfahrt','2006-05-25'),(3146,'Pfingstsonntag','2006-06-04'),(3147,'Pfingstsonntag','2006-06-05'),(3148,'Heiligabend','2006-12-24'),(3149,'1. Weihnachtstag','2006-12-25'),(3150,'2. Weihnachtstag','2006-12-26'),(3151,'Silvester','2006-12-31'),(3152,'Neujahr 2007','2007-01-01'),(3153,'Karfreitag 2007','2007-04-06'),(3154,'Ostersonntag 2007','2007-04-08'),(3155,'Ostermontag 2007','2007-04-09'),(3156,'Maifeiertag 2007','2007-05-01'),(3157,'Christi Himmelfahrt 2007','2007-05-17'),(3158,'Pfingstsonntag 2007','2007-05-27'),(3159,'Pfingstmontag 2007','2007-05-28'),(3160,'Tag der Deutschen Einheit 2007','2007-10-03'),(3161,'Heiligabend 2007','2007-12-24'),(3162,'1. Weihnachtstag 2007','2007-12-25'),(3163,'2. Weihnachtstag 2007','2007-12-26'),(3164,'Silvester 2007','2007-12-31'),(33152,'Neujahr 2008','2008-01-01'),(33153,'Karfreitag 2008','2008-03-21'),(33154,'Ostersonntag 2008','2008-03-23'),(33155,'Ostermontag 2008','2008-03-24'),(33156,'Maifeiertag/Christi Himmelfahrt 2008','2008-05-01'),(33158,'Pfingstsonntag 2008','2008-05-11'),(33159,'Pfingstmontag 2008','2008-05-12'),(33160,'Tag der Deutschen Einheit 2008','2008-10-03'),(33161,'Heiligabend 2008','2008-12-24'),(33162,'1. Weihnachtstag 2008','2008-12-25'),(33163,'2. Weihnachtstag 2008','2008-12-26'),(33164,'Silvester 2008','2008-12-31'),(33201,'Karfreitag','2009-04-10'),(33202,'Ostersonntag','2009-04-12'),(33203,'Ostermontag','2009-04-13'),(33204,'Maifeiertag','2009-05-01'),(33205,'Christi Himmelfahrt','2009-05-21'),(33206,'Pfingstsonntag','2009-05-31'),(33207,'Pfingstmontag','2009-06-01'),(33208,'Heiligabend','2009-12-24'),(33209,'1. Weihnachtstag','2009-12-25'),(33210,'Korrektur für Bugbehebung Feiertage','2009-12-28'),(33211,'Silvester','2009-12-31'),(33212,'Neujahr 2009','2009-01-01'),(33300,'Neujahr 2010','2010-01-01'),(33301,'Karfreitag','2010-04-02'),(33302,'Ostersonntag','2010-04-04'),(33303,'Ostermontag','2010-04-05'),(33305,'Christi Himmelfahrt','2010-05-13'),(33306,'Pfingstsonntag','2010-05-23'),(33307,'Pfingstmontag','2010-05-24'),(33308,'Heiligabend','2010-12-24'),(33310,'2. Weihnachtstag','2010-12-26'),(33311,'Silvester','2010-12-31'),(83191,'Karfreitag','2011-04-22'),(83192,'Ostersonntag','2011-04-24'),(83193,'Ostermontag','2011-04-25'),(83194,'Maifeiertag','2011-05-01'),(83195,'Christi Himmelfahrt','2011-06-02'),(83196,'Pfingstsonntag','2011-06-12'),(83197,'Pfingstsonntag','2011-06-13'),(83199,'1. Weihnachtstag','2011-12-25'),(83200,'2. Weihnachtstag','2011-12-26'),(83201,'Tag der Deutschen Einheit','2011-10-03'),(83221,'Karfreitag','2012-04-06'),(83222,'Ostersonntag','2012-04-08'),(83223,'Ostermontag','2012-04-09'),(83224,'Maifeiertag','2012-05-01'),(83225,'Christi Himmelfahrt','2012-05-17'),(83226,'Pfingstsonntag','2012-05-27'),(83227,'Pfingstmontag','2012-05-28'),(83228,'Tag der Deutschen Einheit','2012-10-03'),(83229,'Heiligabend','2012-12-24'),(83230,'1. Weihnachtstag','2012-12-25'),(83231,'2. Weihnachtstag','2012-12-26'),(83232,'Silvester','2012-12-31'),(83240,'Neujahr 2013','2013-01-01'),(83241,'Karfreitag','2013-03-29'),(83242,'Ostersonntag','2013-03-31'),(83243,'Ostermontag','2013-04-01'),(83244,'Maifeiertag','2013-05-01'),(83245,'Christi Himmelfahrt','2013-05-09'),(83246,'Pfingstsonntag','2013-05-19'),(83247,'Pfingstmontag','2013-05-20'),(83248,'Tag der Deutschen Einheit','2013-10-03'),(83249,'Heiligabend','2013-12-24'),(83250,'1. Weihnachtstag','2013-12-25'),(83251,'2. Weihnachtstag','2013-12-26'),(83252,'Silvester','2013-12-31'),(153600,'Neujahr','2014-01-01'),(153601,'Karfreitag','2014-04-18'),(153602,'Ostersonntag','2014-04-20'),(153603,'Ostermontag','2014-04-21'),(153604,'Maifeiertag','2014-05-01'),(153605,'Christi Himmelfahrt','2014-05-29'),(153606,'Pfingstsonntag','2014-06-08'),(153607,'Pfingstsonntag','2014-06-09'),(153608,'Heiligabend','2014-12-24'),(153609,'1. Weihnachtstag','2014-12-25'),(153610,'2. Weihnachtstag','2014-12-26'),(153611,'Silvester','2014-12-31'),(153612,'Tag der Deutschen Einheit','2014-10-03'),(153620,'Neujahr 2015','2015-01-01'),(153621,'Karfreitag','2015-04-03'),(153622,'Ostersonntag','2015-04-05'),(153623,'Ostermontag','2015-04-06'),(153624,'Maifeiertag','2015-05-01'),(153625,'Christi Himmelfahrt','2015-05-14'),(153626,'Pfingstsonntag','2015-05-24'),(153627,'Pfingstmontag','2015-05-25'),(153629,'Heiligabend','2015-12-24'),(153630,'1. Weihnachtstag','2015-12-25'),(153631,'2. Weihnachtstag','2015-12-26'),(153632,'Silvester','2015-12-31'),(153633,'Neujahr','2016-01-01'),(153634,'Karfreitag','2016-03-25'),(153635,'Ostersonntag','2016-03-27'),(153636,'Ostermontag','2016-03-28'),(153637,'Maifeiertag','2016-05-01'),(153638,'Christi Himmelfahrt','2016-05-05'),(153639,'Pfingstsonntag','2016-05-15'),(153640,'Pfingstmontag','2016-05-16'),(153641,'Tag der Deutschen Einheit','2016-10-03'),(153642,'1. Weihnachtstag','2016-12-25'),(153643,'2. Weihnachtstag','2016-12-26'),(153644,'Heiligabend','2016-12-24'),(153645,'Silverster','2016-12-31'),(153646,'Neujahr','2017-01-01'),(153647,'Karfreitag','2017-04-14'),(153648,'Ostersonntag','2017-04-16'),(153649,'Ostermontag','2017-04-17'),(153650,'Maifeiertag','2017-05-01'),(153651,'Christi Himmelfahrt','2017-05-25'),(153652,'Pfingstsonntag','2017-06-04'),(153653,'Pfingstmontag','2017-06-05'),(153654,'Tag der Deutschen Einheit','2017-10-03'),(153655,'1. Weihnachtstag','2017-12-25'),(153656,'2. Weihnachtstag','2017-12-26'),(153657,'Heiligabend','2017-12-24'),(153658,'Silverster','2017-12-31'),(153659,'Neujahr','2018-01-01'),(153660,'Karfreitag','2018-03-30'),(153661,'Ostersonntag','2018-04-01'),(153662,'Ostermontag','2018-04-02'),(153663,'Maifeiertag','2018-05-01'),(153664,'Christi Himmelfahrt','2018-05-10'),(153665,'Pfingstsonntag','2018-05-20'),(153666,'Pfingstmontag','2018-05-21'),(153667,'Tag der Deutschen Einheit','2018-10-03'),(153668,'1. Weihnachtstag','2018-12-25'),(153669,'2. Weihnachtstag','2018-12-26'),(153670,'Heiligabend','2018-12-24'),(153671,'Silverster','2018-12-31'),(153672,'Neujahr','2019-01-01'),(153673,'Karfreitag','2019-04-19'),(153674,'Ostersonntag','2019-04-21'),(153675,'Ostermontag','2019-04-22'),(153676,'Maifeiertag','2019-05-01'),(153677,'Christi Himmelfahrt','2019-05-30'),(153678,'Pfingstsonntag','2019-06-09'),(153679,'Pfingstmontag','2019-06-10'),(153680,'Tag der Deutschen Einheit','2019-10-03'),(153681,'1. Weihnachtstag','2019-12-25'),(153682,'2. Weihnachtstag','2019-12-26'),(153683,'Heiligabend','2019-12-24'),(153684,'Silverster','2019-12-31'),(153685,'Neujahr','2020-01-01'),(153686,'Karfreitag','2020-04-10'),(153687,'Ostersonntag','2020-04-12'),(153688,'Ostermontag','2020-04-13'),(153689,'Maifeiertag','2020-05-01'),(153690,'Christi Himmelfahrt','2020-05-21'),(153691,'Pfingstsonntag','2020-05-31'),(153692,'Pfingstmontag','2020-06-01'),(153693,'Tag der Deutschen Einheit','2020-10-03'),(153694,'1. Weihnachtstag','2020-12-25'),(153695,'2. Weihnachtstag','2020-12-26'),(153696,'Heiligabend','2020-12-24'),(153697,'Silverster','2020-12-31'),(153698,'Neujahr','2021-01-01'),(153699,'Karfreitag','2021-04-02'),(153700,'Ostersonntag','2021-04-04'),(153701,'Ostermontag','2021-04-05'),(153702,'Maifeiertag','2021-05-01'),(153703,'Christi Himmelfahrt','2021-05-13'),(153704,'Pfingstsonntag','2021-05-23'),(153705,'Pfingstmontag','2021-05-24'),(153706,'Tag der Deutschen Einheit','2021-10-03'),(153707,'1. Weihnachtstag','2021-12-25'),(153708,'2. Weihnachtstag','2021-12-26'),(153709,'Heiligabend','2021-12-24'),(153710,'Silverster','2021-12-31'),(153711,'Neujahr','2022-01-01'),(153712,'Karfreitag','2022-04-15'),(153713,'Ostersonntag','2022-04-17'),(153714,'Ostermontag','2022-04-18'),(153715,'Maifeiertag','2022-05-01'),(153716,'Christi Himmelfahrt','2022-05-26'),(153717,'Pfingstsonntag','2022-06-05'),(153718,'Pfingstmontag','2022-06-06'),(153719,'Tag der Deutschen Einheit','2022-10-03'),(153720,'1. Weihnachtstag','2022-12-25'),(153721,'2. Weihnachtstag','2022-12-26'),(153722,'Heiligabend','2022-12-24'),(153723,'Silverster','2022-12-31'),(153724,'Neujahr','2023-01-01'),(153725,'Karfreitag','2023-04-07'),(153726,'Ostersonntag','2023-04-09'),(153727,'Ostermontag','2023-04-10'),(153728,'Maifeiertag','2023-05-01'),(153729,'Christi Himmelfahrt','2023-05-18'),(153730,'Pfingstsonntag','2023-05-28'),(153731,'Pfingstmontag','2023-05-29'),(153732,'Tag der Deutschen Einheit','2023-10-03'),(153733,'1. Weihnachtstag','2023-12-25'),(153734,'2. Weihnachtstag','2023-12-26'),(153735,'Heiligabend','2023-12-24'),(153736,'Silverster','2023-12-31'),(153737,'Neujahr','2024-01-01'),(153738,'Karfreitag','2024-03-29'),(153739,'Ostersonntag','2024-03-31'),(153740,'Ostermontag','2024-04-01'),(153741,'Maifeiertag','2024-05-01'),(153742,'Christi Himmelfahrt','2024-05-09'),(153743,'Pfingstsonntag','2024-05-19'),(153744,'Pfingstmontag','2024-05-20'),(153745,'Tag der Deutschen Einheit','2024-10-03'),(153746,'1. Weihnachtstag','2024-12-25'),(153747,'2. Weihnachtstag','2024-12-26'),(153748,'Heiligabend','2024-12-24'),(153749,'Silverster','2024-12-31'),(153750,'Neujahr','2025-01-01'),(153751,'Karfreitag','2025-04-18'),(153752,'Ostersonntag','2025-04-20'),(153753,'Ostermontag','2025-04-21'),(153754,'Maifeiertag','2025-05-01'),(153755,'Christi Himmelfahrt','2025-05-29'),(153756,'Pfingstsonntag','2025-06-08'),(153757,'Pfingstmontag','2025-06-09'),(153758,'Tag der Deutschen Einheit','2025-10-03'),(153759,'1. Weihnachtstag','2025-12-25'),(153760,'2. Weihnachtstag','2025-12-26'),(153761,'Heiligabend','2025-12-24'),(153762,'Silverster','2025-12-31'),(153763,'Reformationstag','2017-10-31');
/*!40000 ALTER TABLE `publicholiday` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `referenceday`
--

DROP TABLE IF EXISTS `referenceday`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `referenceday` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `dow` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `holiday` bit(1) DEFAULT NULL,
  `name` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `refdate` date DEFAULT NULL,
  `workingday` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=181031 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `referenceday`
--

LOCK TABLES `referenceday` WRITE;
/*!40000 ALTER TABLE `referenceday` DISABLE KEYS */;
INSERT INTO `referenceday` VALUES (181025,'Wed','\0','','2019-09-11',''),(181026,'Thu','\0','','2019-09-12',''),(181027,'Fri','\0','','2019-09-13',''),(181028,'Mon','\0','','2019-09-16',''),(181029,'Tue','\0','','2019-09-17',''),(181030,'Wed','\0','','2019-09-18','');
/*!40000 ALTER TABLE `referenceday` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `statusreport`
--

DROP TABLE IF EXISTS `statusreport`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `statusreport` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `accepted` datetime DEFAULT NULL,
  `aim_action` text COLLATE utf8_bin DEFAULT NULL,
  `aim_source` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `aim_status` tinyint(4) DEFAULT NULL,
  `aim_text` text COLLATE utf8_bin DEFAULT NULL,
  `allocator` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `budget_resources_date_action` text COLLATE utf8_bin DEFAULT NULL,
  `budget_resources_date_source` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `budget_resources_date_status` tinyint(4) DEFAULT NULL,
  `budget_resources_date_text` text COLLATE utf8_bin DEFAULT NULL,
  `changedirective_action` text COLLATE utf8_bin DEFAULT NULL,
  `changedirective_source` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `changedirective_status` tinyint(4) DEFAULT NULL,
  `changedirective_text` text COLLATE utf8_bin DEFAULT NULL,
  `communication_action` text COLLATE utf8_bin DEFAULT NULL,
  `communication_source` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `communication_status` tinyint(4) DEFAULT NULL,
  `communication_text` text COLLATE utf8_bin DEFAULT NULL,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `customerfeedback_source` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `customerfeedback_status` tinyint(4) DEFAULT NULL,
  `customerfeedback_text` text COLLATE utf8_bin DEFAULT NULL,
  `fromdate` date DEFAULT NULL,
  `improvement_action` text COLLATE utf8_bin DEFAULT NULL,
  `improvement_source` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `improvement_status` tinyint(4) DEFAULT NULL,
  `improvement_text` text COLLATE utf8_bin DEFAULT NULL,
  `lastupdate` datetime DEFAULT NULL,
  `lastupdatedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `miscellaneous_action` text COLLATE utf8_bin DEFAULT NULL,
  `miscellaneous_source` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `miscellaneous_status` tinyint(4) DEFAULT NULL,
  `miscellaneous_text` text COLLATE utf8_bin DEFAULT NULL,
  `needforaction_source` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `needforaction_status` tinyint(4) DEFAULT NULL,
  `needforaction_text` text COLLATE utf8_bin DEFAULT NULL,
  `notes` text COLLATE utf8_bin DEFAULT NULL,
  `phase` tinyint(4) DEFAULT NULL,
  `released` datetime DEFAULT NULL,
  `riskmonitoring_action` text COLLATE utf8_bin DEFAULT NULL,
  `riskmonitoring_source` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `riskmonitoring_status` tinyint(4) DEFAULT NULL,
  `riskmonitoring_text` text COLLATE utf8_bin DEFAULT NULL,
  `sort` tinyint(4) DEFAULT NULL,
  `trend` tinyint(4) DEFAULT NULL,
  `trendstatus` tinyint(4) DEFAULT NULL,
  `untildate` date DEFAULT NULL,
  `updatecounter` int(11) DEFAULT NULL,
  `ACCEPTEDBY` bigint(20) DEFAULT NULL,
  `CUSTOMERORDER` bigint(20) DEFAULT NULL,
  `RECIPIENT` bigint(20) DEFAULT NULL,
  `RELEASEDBY` bigint(20) DEFAULT NULL,
  `SENDER` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3B05CFA6545F27E4` (`ACCEPTEDBY`),
  KEY `FK3B05CFA66779E3DB` (`SENDER`),
  KEY `FK3B05CFA6430C53FA` (`RELEASEDBY`),
  KEY `FK3B05CFA6CE5B3BBF` (`RECIPIENT`),
  KEY `FK3B05CFA6800EBC68` (`CUSTOMERORDER`),
  CONSTRAINT `FK3B05CFA6430C53FA` FOREIGN KEY (`RELEASEDBY`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK3B05CFA6545F27E4` FOREIGN KEY (`ACCEPTEDBY`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK3B05CFA66779E3DB` FOREIGN KEY (`SENDER`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK3B05CFA6800EBC68` FOREIGN KEY (`CUSTOMERORDER`) REFERENCES `customerorder` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK3B05CFA6CE5B3BBF` FOREIGN KEY (`RECIPIENT`) REFERENCES `employee` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=180208 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `statusreport`
--

LOCK TABLES `statusreport` WRITE;
/*!40000 ALTER TABLE `statusreport` DISABLE KEYS */;
/*!40000 ALTER TABLE `statusreport` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `suborder`
--

DROP TABLE IF EXISTS `suborder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `suborder` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `commentnecessary` bit(1) DEFAULT NULL,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `currency` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `debithours` double DEFAULT NULL,
  `debithoursunit` tinyint(4) DEFAULT NULL,
  `description` text COLLATE utf8_bin DEFAULT NULL,
  `fixedPrice` bit(1) DEFAULT NULL,
  `fromDate` date DEFAULT NULL,
  `hide` bit(1) DEFAULT NULL,
  `hourly_rate` double DEFAULT NULL,
  `invoice` char(1) COLLATE utf8_bin NOT NULL,
  `lastupdate` datetime DEFAULT NULL,
  `lastupdatedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `NOEMPLOYEEORDERCONTENT` bit(1) DEFAULT NULL,
  `shortdescription` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `sign` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `standard` bit(1) DEFAULT NULL,
  `suborder_customer` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `trainingFlag` bit(1) DEFAULT NULL,
  `untilDate` date DEFAULT NULL,
  `updatecounter` int(11) DEFAULT NULL,
  `CUSTOMERORDER_ID` bigint(20) DEFAULT NULL,
  `PARENTORDER_ID` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK88C7152EA76A525C` (`PARENTORDER_ID`),
  KEY `FK88C7152E42103BC2` (`CUSTOMERORDER_ID`),
  CONSTRAINT `FK88C7152E42103BC2` FOREIGN KEY (`CUSTOMERORDER_ID`) REFERENCES `customerorder` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK88C7152EA76A525C` FOREIGN KEY (`PARENTORDER_ID`) REFERENCES `suborder` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=180693 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `suborder`
--

LOCK TABLES `suborder` WRITE;
/*!40000 ALTER TABLE `suborder` DISABLE KEYS */;
INSERT INTO `suborder` VALUES (5861,'\0','2006-12-22 14:14:18','adm','EUR',NULL,NULL,'Krankheit 2019','\0','2019-01-01','\0',0,'N','2009-02-02 10:42:35','adm','\0','Krankheit 2019','2019','','','\0',NULL,3,5191,NULL),
                              (60208,'\0','2010-01-04 13:27:24','adm','EUR',NULL,NULL,'Urlaub 2019','\0','2019-01-01','\0',0,'N','2011-05-17 14:23:53','adm','\0','Urlaub 2019','2019','','','\0',NULL,3,5190,NULL),
                              (180692,'\0','2019-09-11 04:53:23','bm','EUR',NULL,NULL,'Testen der Stuhlpolsterung','\0','2019-09-11','\0',100,'Y',NULL,NULL,'\0','Stuhlpolsterung','111.01','\0','SP1','\0',NULL,NULL,180969,NULL);
/*!40000 ALTER TABLE `suborder` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `timereport`
--

DROP TABLE IF EXISTS `timereport`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `timereport` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `accepted` datetime DEFAULT NULL,
  `acceptedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `costs` double DEFAULT NULL,
  `created` datetime DEFAULT NULL,
  `createdby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `durationhours` int(11) DEFAULT NULL,
  `durationminutes` int(11) DEFAULT NULL,
  `lastupdate` datetime DEFAULT NULL,
  `lastupdatedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `released` datetime DEFAULT NULL,
  `releasedby` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `sequencenumber` int(11) NOT NULL,
  `sortofreport` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `status` varchar(255) COLLATE utf8_bin DEFAULT NULL,
  `taskdescription` text COLLATE utf8_bin DEFAULT NULL,
  `training` bit(1) DEFAULT NULL,
  `updatecounter` int(11) DEFAULT NULL,
  `EMPLOYEECONTRACT_ID` bigint(20) DEFAULT NULL,
  `EMPLOYEEORDER_ID` bigint(20) DEFAULT NULL,
  `REFERENCEDAY_ID` bigint(20) DEFAULT NULL,
  `SUBORDER_ID` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKDA2C7661789484B2` (`EMPLOYEECONTRACT_ID`),
  KEY `FKDA2C76616F5511F2` (`SUBORDER_ID`),
  KEY `FKDA2C766196C42DC2` (`EMPLOYEEORDER_ID`),
  KEY `FKDA2C7661EC9FF712` (`REFERENCEDAY_ID`),
  CONSTRAINT `FKDA2C76616F5511F2` FOREIGN KEY (`SUBORDER_ID`) REFERENCES `suborder` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKDA2C7661789484B2` FOREIGN KEY (`EMPLOYEECONTRACT_ID`) REFERENCES `employeecontract` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKDA2C766196C42DC2` FOREIGN KEY (`EMPLOYEEORDER_ID`) REFERENCES `employeeorder` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKDA2C7661EC9FF712` FOREIGN KEY (`REFERENCEDAY_ID`) REFERENCES `referenceday` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=214175 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `timereport`
--

LOCK TABLES `timereport` WRITE;
/*!40000 ALTER TABLE `timereport` DISABLE KEYS */;
INSERT INTO `timereport` VALUES (214169,NULL,NULL,0,'2019-09-11 04:55:58','tt',8,0,NULL,NULL,NULL,NULL,1,'W','open','Nichts gemacht','\0',NULL,180662,183209,181025,180692),(214170,NULL,NULL,0,'2019-09-11 04:57:03','tt',8,0,NULL,NULL,NULL,NULL,0,'W','open','Einfach nur rumgesessen','\0',NULL,180662,183209,181026,180692),(214171,NULL,NULL,0,'2019-09-11 04:57:04','tt',8,0,NULL,NULL,NULL,NULL,0,'W','open','Einfach nur rumgesessen','\0',NULL,180662,183209,181027,180692),(214172,NULL,NULL,0,'2019-09-11 04:57:04','tt',8,0,NULL,NULL,NULL,NULL,0,'W','open','Einfach nur rumgesessen','\0',NULL,180662,183209,181028,180692),(214173,NULL,NULL,0,'2019-09-11 04:57:04','tt',8,0,NULL,NULL,NULL,NULL,0,'W','open','Einfach nur rumgesessen','\0',NULL,180662,183209,181029,180692),(214174,NULL,NULL,0,'2019-09-11 04:57:04','tt',8,0,NULL,NULL,NULL,NULL,0,'W','open','Einfach nur rumgesessen','\0',NULL,180662,183209,181030,180692);
/*!40000 ALTER TABLE `timereport` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `vacation`
--

DROP TABLE IF EXISTS `vacation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `vacation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `entitlement` int(11) DEFAULT NULL,
  `used` int(11) DEFAULT NULL,
  `year` int(11) DEFAULT NULL,
  `EMPLOYEECONTRACT_ID` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9F5E86FD789484B2` (`EMPLOYEECONTRACT_ID`),
  CONSTRAINT `FK9F5E86FD789484B2` FOREIGN KEY (`EMPLOYEECONTRACT_ID`) REFERENCES `employeecontract` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=180653 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `vacation`
--

LOCK TABLES `vacation` WRITE;
/*!40000 ALTER TABLE `vacation` DISABLE KEYS */;
INSERT INTO `vacation` VALUES (180651,30,0,2019,180661),(180652,30,0,2019,180662);
/*!40000 ALTER TABLE `vacation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `workingday`
--

DROP TABLE IF EXISTS `workingday`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `workingday` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `Starttimehour` int(11) NOT NULL,
  `Starttimeminute` int(11) NOT NULL,
  `breakhours` int(11) NOT NULL,
  `breakminutes` int(11) NOT NULL,
  `refday` date DEFAULT NULL,
  `EMPLOYEECONTRACT_ID` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK668D9AEB789484B2` (`EMPLOYEECONTRACT_ID`),
  CONSTRAINT `FK668D9AEB789484B2` FOREIGN KEY (`EMPLOYEECONTRACT_ID`) REFERENCES `employeecontract` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=183915 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `workingday`
--

LOCK TABLES `workingday` WRITE;
/*!40000 ALTER TABLE `workingday` DISABLE KEYS */;
/*!40000 ALTER TABLE `workingday` ENABLE KEYS */;
UNLOCK TABLES;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2019-09-11  7:07:08

DROP TABLE IF EXISTS `favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `favorite` (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT,
                            `employeeId`bigint(20) NOT NULL,
                            `employeeorderId` bigint(20) NOT NULL,
                            `hours` int(11) NOT NULL,
                            `minutes` int(11) NOT NULL,
                            `comment` text COLLATE utf8_bin DEFAULT NULL,
                            PRIMARY KEY (`id`),
) ENGINE=InnoDB AUTO_INCREMENT=183915 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
