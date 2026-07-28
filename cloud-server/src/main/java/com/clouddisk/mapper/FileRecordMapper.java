package com.clouddisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clouddisk.entity.FileRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface FileRecordMapper extends BaseMapper<FileRecord> {

    @Select("""
            SELECT *
            FROM file
            WHERE id = #{id}
              AND user_id = #{userId}
              AND node_type = 'FOLDER'
              AND is_delete = 0
            LIMIT 1
            """)
    FileRecord selectOwnedActiveFolderById(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            SELECT *
            FROM file
            WHERE id = #{id}
              AND user_id = #{userId}
              AND node_type = 'FOLDER'
              AND is_delete = 0
            LIMIT 1
            FOR UPDATE
            """)
    FileRecord selectOwnedActiveFolderByIdForUpdate(@Param("id") Long id,
                                                    @Param("userId") Long userId);

    @Select("""
            SELECT *
            FROM file
            WHERE user_id = #{userId}
              AND parent_id = #{parentId}
              AND file_name = #{fileName}
              AND is_delete = 0
            LIMIT 1
            """)
    FileRecord selectActiveDuplicate(@Param("userId") Long userId,
                                     @Param("parentId") Long parentId,
                                     @Param("fileName") String fileName);

    @Update("""
            UPDATE file
            SET file_name = #{fileName},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND user_id = #{userId}
              AND is_delete = 0
            """)
    int updateOwnedActiveName(@Param("id") Long id,
                              @Param("userId") Long userId,
                              @Param("fileName") String fileName);

    @Select("""
            SELECT *
            FROM file
            WHERE user_id = #{userId}
              AND parent_id = #{parentId}
              AND is_delete = 0
            ORDER BY
              CASE WHEN #{sortColumn} = 'file_name' AND #{sortOrder} = 'ASC' THEN file_name END ASC,
              CASE WHEN #{sortColumn} = 'file_name' AND #{sortOrder} = 'DESC' THEN file_name END DESC,
              CASE WHEN #{sortColumn} = 'file_size' AND #{sortOrder} = 'ASC' THEN file_size END ASC,
              CASE WHEN #{sortColumn} = 'file_size' AND #{sortOrder} = 'DESC' THEN file_size END DESC,
              CASE WHEN #{sortColumn} = 'file_type' AND #{sortOrder} = 'ASC' THEN file_type END ASC,
              CASE WHEN #{sortColumn} = 'file_type' AND #{sortOrder} = 'DESC' THEN file_type END DESC,
              CASE WHEN #{sortColumn} = 'create_time' AND #{sortOrder} = 'ASC' THEN create_time END ASC,
              CASE WHEN #{sortColumn} = 'create_time' AND #{sortOrder} = 'DESC' THEN create_time END DESC,
              CASE WHEN #{sortColumn} = 'update_time' AND #{sortOrder} = 'ASC' THEN update_time END ASC,
              CASE WHEN #{sortColumn} = 'update_time' AND #{sortOrder} = 'DESC' THEN update_time END DESC,
              CASE WHEN #{sortOrder} = 'ASC' THEN id END ASC,
              CASE WHEN #{sortOrder} = 'DESC' THEN id END DESC
            """)
    List<FileRecord> selectOwnedActiveChildren(@Param("userId") Long userId,
                                               @Param("parentId") Long parentId,
                                               @Param("sortColumn") String sortColumn,
                                               @Param("sortOrder") String sortOrder);

    @Select("""
            SELECT *
            FROM file
            WHERE user_id = #{userId}
              AND is_delete = 1
            ORDER BY update_time DESC, id DESC
            """)
    List<FileRecord> selectOwnedDeleted(@Param("userId") Long userId);

    @Select("""
            SELECT *
            FROM file
            WHERE id = #{id}
              AND user_id = #{userId}
              AND is_delete = 0
            LIMIT 1
            """)
    FileRecord selectOwnedActiveById(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            SELECT *
            FROM file
            WHERE id = #{id}
              AND user_id = #{userId}
              AND is_delete = 0
            LIMIT 1
            FOR UPDATE
            """)
    FileRecord selectOwnedActiveByIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            SELECT *
            FROM file
            WHERE user_id = #{userId}
              AND is_delete = 0
              AND LOWER(file_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')
            ORDER BY CASE WHEN node_type = 'FOLDER' THEN 0 ELSE 1 END,
                     update_time DESC, id DESC
            LIMIT 100
            """)
    List<FileRecord> selectOwnedActiveSearch(@Param("userId") Long userId,
                                             @Param("keyword") String keyword);

    @Update("""
            UPDATE file
            SET parent_id = #{parentId},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND user_id = #{userId}
              AND is_delete = 0
            """)
    int updateOwnedActiveParent(@Param("id") Long id,
                                @Param("userId") Long userId,
                                @Param("parentId") Long parentId);

    @Select("""
            SELECT *
            FROM file
            WHERE id = #{id}
              AND is_delete = 0
            LIMIT 1
            """)
    FileRecord selectActiveById(@Param("id") Long id);

    @Select("""
            SELECT *
            FROM file
            WHERE id = #{id}
              AND user_id = #{userId}
              AND is_delete = 1
            LIMIT 1
            """)
    FileRecord selectOwnedDeletedById(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            WITH RECURSIVE subtree (id, node_type, depth) AS (
                SELECT id, node_type, 0
                FROM file
                WHERE id = #{id}
                  AND user_id = #{userId}
                  AND is_delete = 1
                UNION ALL
                SELECT child.id, child.node_type, parent.depth + 1
                FROM subtree parent
                JOIN file child ON child.parent_id = parent.id
                WHERE child.user_id = #{userId}
                  AND child.is_delete = 1
                  AND parent.node_type = 'FOLDER'
                  AND parent.depth < 32
            )
            SELECT file.*
            FROM file
            JOIN subtree ON subtree.id = file.id
            WHERE file.user_id = #{userId}
              AND file.is_delete = 1
            """)
    List<FileRecord> selectOwnedDeletedSubtree(@Param("id") Long id,
                                               @Param("userId") Long userId);

    @Select("""
            WITH RECURSIVE subtree (id, node_type, depth) AS (
                SELECT id, node_type, 0
                FROM file
                WHERE id = #{id}
                  AND user_id = #{userId}
                  AND is_delete = #{isDelete}
                UNION ALL
                SELECT child.id, child.node_type, parent.depth + 1
                FROM subtree parent
                JOIN file child ON child.parent_id = parent.id
                WHERE child.user_id = #{userId}
                  AND child.is_delete = #{isDelete}
                  AND parent.node_type = 'FOLDER'
                  AND parent.depth < 32
            )
            SELECT id
            FROM subtree
            WHERE node_type = 'FOLDER'
            """)
    List<Long> selectOwnedFolderIdsInSubtree(@Param("id") Long id,
                                             @Param("userId") Long userId,
                                             @Param("isDelete") Integer isDelete);

    @Select("""
            WITH RECURSIVE subtree (id, node_type, depth) AS (
                SELECT id, node_type, 0
                FROM file
                WHERE id = #{id}
                  AND user_id = #{userId}
                  AND is_delete = 0
                UNION ALL
                SELECT child.id, child.node_type, parent.depth + 1
                FROM subtree parent
                JOIN file child ON child.parent_id = parent.id
                WHERE child.user_id = #{userId}
                  AND child.is_delete = 0
                  AND parent.node_type = 'FOLDER'
                  AND parent.depth < 32
            )
            SELECT id
            FROM subtree
            """)
    List<Long> selectOwnedActiveIdsInSubtree(@Param("id") Long id,
                                             @Param("userId") Long userId);

    @Update("""
            <script>
            UPDATE file
            SET is_delete = 1,
                update_time = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
              AND is_delete = 0
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    int softDeleteOwnedActiveByIds(@Param("ids") List<Long> ids,
                                   @Param("userId") Long userId);

    /**
     * Legacy single-root delete entry point kept for mapper compatibility.
     * New service code uses the explicit subtree expansion plus batch method above.
     */
    @Update("""
            WITH RECURSIVE subtree (id, node_type, depth) AS (
                SELECT id, node_type, 0
                FROM file
                WHERE id = #{id}
                  AND user_id = #{userId}
                  AND is_delete = 0
                UNION ALL
                SELECT child.id, child.node_type, parent.depth + 1
                FROM subtree parent
                JOIN file child ON child.parent_id = parent.id
                WHERE child.user_id = #{userId}
                  AND child.is_delete = 0
                  AND parent.node_type = 'FOLDER'
                  AND parent.depth < 32
            )
            UPDATE file
            SET is_delete = 1,
                update_time = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
              AND is_delete = 0
              AND id IN (SELECT id FROM subtree)
            """)
    int softDeleteOwnedActive(@Param("id") Long id,
                              @Param("userId") Long userId);

    @Delete("""
            <script>
            DELETE FROM file
            WHERE user_id = #{userId}
              AND is_delete = 1
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    int deleteOwnedDeletedByIds(@Param("ids") List<Long> ids,
                                @Param("userId") Long userId);

    @Update("""
            WITH RECURSIVE subtree (id, node_type, depth) AS (
                SELECT id, node_type, 0
                FROM file
                WHERE id = #{id}
                  AND user_id = #{userId}
                  AND is_delete = 1
                UNION ALL
                SELECT child.id, child.node_type, parent.depth + 1
                FROM subtree parent
                JOIN file child ON child.parent_id = parent.id
                WHERE child.user_id = #{userId}
                  AND child.is_delete = 1
                  AND parent.node_type = 'FOLDER'
                  AND parent.depth < 32
            )
            UPDATE file
            JOIN subtree ON subtree.id = file.id
            SET file.is_delete = 0,
                file.update_time = CURRENT_TIMESTAMP
            WHERE file.user_id = #{userId}
              AND file.is_delete = 1
            """)
    int recoverOwnedDeleted(@Param("id") Long id, @Param("userId") Long userId);
}
