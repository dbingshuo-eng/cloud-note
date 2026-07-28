package com.clouddisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file")
public class FileRecord {

    public static final String NODE_TYPE_FILE = "FILE";
    public static final String NODE_TYPE_FOLDER = "FOLDER";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long parentId;

    private String fileName;

    private Long fileSize;

    private String fileType;

    private String nodeType;

    private String fileUrl;

    private String filePath;

    private String md5;

    @TableField("is_delete")
    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
