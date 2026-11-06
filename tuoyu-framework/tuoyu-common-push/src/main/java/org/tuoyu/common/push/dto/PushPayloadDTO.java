package org.tuoyu.common.push.dto;

import lombok.Data;
import org.tuoyu.common.core.enums.PushSourceEnum;
import org.tuoyu.common.core.enums.PushTypeEnum;
import org.tuoyu.common.core.utils.StringUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 推送给前端的统一消息体
 *
 * @author Lion Li
 */
@Data
public class PushPayloadDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long messageId;

    private String type;

    private String source;

    private String message;

    private Object data;

    private String path;

    private Long timestamp;

    public static PushPayloadDTO of(String type, String source, String message, Object data) {
        PushPayloadDTO payload = new PushPayloadDTO();
        payload.setType(StringUtils.defaultIfBlank(type, PushTypeEnum.MESSAGE.getType()));
        payload.setSource(StringUtils.defaultIfBlank(source, PushSourceEnum.BACKEND.getSource()));
        payload.setMessage(message);
        payload.setData(data);
        payload.setTimestamp(System.currentTimeMillis());
        return payload;
    }

    public static PushPayloadDTO of(PushTypeEnum type, PushSourceEnum source, String message, Object data) {
        return of(
            type == null ? null : type.getType(),
            source == null ? null : source.getSource(),
            message,
            data
        );
    }

    public static PushPayloadDTO of(PushTypeEnum type, PushSourceEnum source, String message, Object data, String path) {
        PushPayloadDTO payload = of(type, source, message, data);
        payload.setPath(path);
        return payload;
    }
}
