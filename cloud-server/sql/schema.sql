CREATE DATABASE IF NOT EXISTS cloud_disk
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE cloud_disk;

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    openid VARCHAR(128) NOT NULL COMMENT 'WeChat openid',
    nickname VARCHAR(64) NOT NULL COMMENT 'User nickname',
    avatar VARCHAR(255) DEFAULT NULL COMMENT 'User avatar URL',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Cloud disk users';

CREATE TABLE IF NOT EXISTS file (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT 'Parent folder id',
    file_name VARCHAR(255) NOT NULL COMMENT 'Display file name',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT 'File size in bytes',
    file_type VARCHAR(64) DEFAULT NULL COMMENT 'File type or suffix',
    node_type VARCHAR(16) NOT NULL DEFAULT 'FILE' COMMENT 'Hierarchy node type',
    file_url VARCHAR(512) DEFAULT NULL COMMENT 'File URL',
    file_path VARCHAR(512) DEFAULT NULL COMMENT 'Object storage path',
    md5 VARCHAR(64) DEFAULT NULL COMMENT 'File MD5',
    is_delete TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag',
    active_file_name VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN is_delete = 0 THEN file_name ELSE NULL END) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_owner_parent_active_name (user_id, parent_id, active_file_name),
    KEY idx_file_user_parent (user_id, parent_id),
    KEY idx_file_user_delete_parent_update (user_id, is_delete, parent_id, update_time),
    KEY idx_file_delete (is_delete),
    CONSTRAINT chk_file_node_type CHECK (node_type IN ('FILE', 'FOLDER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Cloud disk files';

-- Upgrade legacy tables by inferring old folders from their no-object representation.
-- A stored object named *.folder remains FILE because file_path/md5 are present.
SET @node_type_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'file'
      AND COLUMN_NAME = 'node_type'
);
SET @add_node_type_column = IF(
    @node_type_column_exists = 0,
    'ALTER TABLE file ADD COLUMN node_type VARCHAR(16) NOT NULL DEFAULT ''FILE'' COMMENT ''Hierarchy node type'' AFTER file_type',
    'SELECT 1'
);
PREPARE add_node_type_column_statement FROM @add_node_type_column;
EXECUTE add_node_type_column_statement;
DEALLOCATE PREPARE add_node_type_column_statement;

SET @classify_legacy_node_types =
    'UPDATE file SET node_type = ''FOLDER'' WHERE node_type = ''FILE'' AND file_type = ''folder'' AND file_size = 0 AND file_url IS NULL AND file_path IS NULL AND md5 IS NULL';
PREPARE classify_legacy_node_types_statement FROM @classify_legacy_node_types;
EXECUTE classify_legacy_node_types_statement;
DEALLOCATE PREPARE classify_legacy_node_types_statement;

SET @node_type_constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'file'
      AND CONSTRAINT_NAME = 'chk_file_node_type'
);
SET @add_node_type_constraint = IF(
    @node_type_constraint_exists = 0,
    'ALTER TABLE file ADD CONSTRAINT chk_file_node_type CHECK (node_type IN (''FILE'', ''FOLDER''))',
    'SELECT 1'
);
PREPARE add_node_type_constraint_statement FROM @add_node_type_constraint;
EXECUTE add_node_type_constraint_statement;
DEALLOCATE PREPARE add_node_type_constraint_statement;

-- Upgrade an existing Phase 1 file table without failing when this schema is re-run.
SET @active_file_name_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'file'
      AND COLUMN_NAME = 'active_file_name'
);
SET @add_active_file_name_column = IF(
    @active_file_name_column_exists = 0,
    'ALTER TABLE file ADD COLUMN active_file_name VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN is_delete = 0 THEN file_name ELSE NULL END) STORED AFTER is_delete',
    'SELECT 1'
);
PREPARE add_active_file_name_column_statement FROM @add_active_file_name_column;
EXECUTE add_active_file_name_column_statement;
DEALLOCATE PREPARE add_active_file_name_column_statement;

SET @active_file_name_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'file'
      AND INDEX_NAME = 'uk_file_owner_parent_active_name'
);
SET @add_active_file_name_index = IF(
    @active_file_name_index_exists = 0,
    'ALTER TABLE file ADD UNIQUE INDEX uk_file_owner_parent_active_name (user_id, parent_id, active_file_name)',
    'SELECT 1'
);
PREPARE add_active_file_name_index_statement FROM @add_active_file_name_index;
EXECUTE add_active_file_name_index_statement;
DEALLOCATE PREPARE add_active_file_name_index_statement;

SET @phase_three_file_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'file'
      AND INDEX_NAME = 'idx_file_user_delete_parent_update'
);
SET @add_phase_three_file_index = IF(
    @phase_three_file_index_exists = 0,
    'ALTER TABLE file ADD INDEX idx_file_user_delete_parent_update (user_id, is_delete, parent_id, update_time)',
    'SELECT 1'
);
PREPARE add_phase_three_file_index_statement FROM @add_phase_three_file_index;
EXECUTE add_phase_three_file_index_statement;
DEALLOCATE PREPARE add_phase_three_file_index_statement;

CREATE TABLE IF NOT EXISTS share (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    file_id BIGINT NOT NULL COMMENT 'Shared file id',
    share_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Unique case-sensitive share code',
    share_password VARCHAR(255) DEFAULT NULL COMMENT 'BCrypt password hash; never plaintext',
    expire_time DATETIME DEFAULT NULL COMMENT 'Expiration time',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_share_code (share_code),
    KEY idx_share_file_id (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Cloud disk share links';

-- Upgrade existing share-code columns so equality and uniqueness stay case-sensitive.
SET @share_code_requires_binary_collation = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'share'
      AND COLUMN_NAME = 'share_code'
      AND (
          CHARACTER_SET_NAME <> 'utf8mb4'
          OR COLLATION_NAME <> 'utf8mb4_bin'
      )
);
SET @upgrade_share_code_collation = IF(
    @share_code_requires_binary_collation > 0,
    'ALTER TABLE share MODIFY COLUMN share_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT ''Unique case-sensitive share code''',
    'SELECT 1'
);
PREPARE upgrade_share_code_collation_statement FROM @upgrade_share_code_collation;
EXECUTE upgrade_share_code_collation_statement;
DEALLOCATE PREPARE upgrade_share_code_collation_statement;
