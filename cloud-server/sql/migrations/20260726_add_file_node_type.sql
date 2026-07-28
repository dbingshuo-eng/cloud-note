USE cloud_disk;

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
