/*
Navicat MySQL Data Transfer

Source Server         : localhost-trias
Source Server Version : 50644
Source Host           : 192.168.199.126:3306
Source Database       : trias_cli

Target Server Type    : MYSQL
Target Server Version : 50644
File Encoding         : 65001

Date: 2019-05-17 15:01:24
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for trias_resource
-- ----------------------------
DROP TABLE IF EXISTS `trias_resource`;
CREATE TABLE `trias_resource` (
  `id` int(10) unsigned zerofill NOT NULL AUTO_INCREMENT,
  `root_name` varchar(255) NOT NULL,
  `path` varchar(255) NOT NULL,
  `del_flag` int(1) NOT NULL DEFAULT '0',
  `description` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of trias_resource
-- ----------------------------
INSERT INTO `trias_resource` VALUES ('0000000001', 'user', '/user/leviatom', '0', null);
INSERT INTO `trias_resource` VALUES ('0000000002', 'user', '/user/streamNet', '0', null);
INSERT INTO `trias_resource` VALUES ('0000000003', 'user', '/user/netCoin', '0', null);
INSERT INTO `trias_resource` VALUES ('0000000004', 'server', '/server/devOps', '0', null);
INSERT INTO `trias_resource` VALUES ('0000000005', 'server', '/server/deployment', '0', null);
INSERT INTO `trias_resource` VALUES ('0000000006', 'server', '/server/experiment', '0', null);

-- ----------------------------
-- Table structure for trias_role
-- ----------------------------
DROP TABLE IF EXISTS `trias_role`;
CREATE TABLE `trias_role` (
  `id` int(11) unsigned zerofill NOT NULL AUTO_INCREMENT,
  `role_type` varchar(255) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of trias_role
-- ----------------------------
INSERT INTO `trias_role` VALUES ('00000000001', 'ROLE_ADMIN', null);
INSERT INTO `trias_role` VALUES ('00000000002', 'ROLE_USER', null);

-- ----------------------------
-- Table structure for trias_role_resource
-- ----------------------------
DROP TABLE IF EXISTS `trias_role_resource`;
CREATE TABLE `trias_role_resource` (
  `id` int(11) unsigned zerofill NOT NULL AUTO_INCREMENT,
  `role_id` int(11) NOT NULL,
  `resource_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of trias_role_resource
-- ----------------------------
INSERT INTO `trias_role_resource` VALUES ('00000000001', '1', '1');
INSERT INTO `trias_role_resource` VALUES ('00000000002', '1', '2');
INSERT INTO `trias_role_resource` VALUES ('00000000003', '1', '3');
INSERT INTO `trias_role_resource` VALUES ('00000000004', '1', '4');
INSERT INTO `trias_role_resource` VALUES ('00000000005', '1', '5');
INSERT INTO `trias_role_resource` VALUES ('00000000006', '1', '6');
INSERT INTO `trias_role_resource` VALUES ('00000000007', '2', '1');
INSERT INTO `trias_role_resource` VALUES ('00000000008', '2', '2');
INSERT INTO `trias_role_resource` VALUES ('00000000009', '2', '3');
