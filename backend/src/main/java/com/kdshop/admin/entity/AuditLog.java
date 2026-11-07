package com.kdshop.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String actorType;
    private Long actorId;
    private String action;
    private String targetType;
    private String targetId;
    private String beforeJson;
    private String afterJson;
    private String ip;
    private LocalDateTime createdAt;
}
