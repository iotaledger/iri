CREATE TABLE `trias_cli_user` (
`id`  int ZEROFILL NOT NULL AUTO_INCREMENT ,
`username`  varchar(255) NOT NULL ,
`account`  varchar(255) NULL DEFAULT '' ,
`sex`  varchar(255) NULL ,
`email`  varchar(255) NULL ,
PRIMARY KEY (`id`),
UNIQUE INDEX `idx_unique_username` (`username`) ,
UNIQUE INDEX `idx_unique_account` (`account`) 
)
ENGINE=InnoDB
DEFAULT CHARACTER SET=utf8 COLLATE=utf8_general_ci
;


ALTER TABLE `trias_cli_user`
ADD COLUMN `create_time`  datetime NULL AFTER `email`,
ADD COLUMN `update_time`  datetime NULL AFTER `create_time`;