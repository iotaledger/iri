ALTER TABLE `trias_user`
ADD COLUMN `create_time`  datetime NULL AFTER `role_id`,
ADD COLUMN `update_time`  datetime NULL AFTER `create_time`,
ADD UNIQUE INDEX `idx_unique_username` (`username`) ;

