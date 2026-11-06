package org.tuoyu.resource.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息记录视图对象
 *
 * @author Lion Li
 */
@Data
public class SysMessageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long messageId;

    private String category;

    private String type;

    private String source;

    private String title;

    private String message;

    private String content;

    private Object data;

    private String path;

    private LocalDateTime createTime;
}
