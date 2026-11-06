package org.tuoyu.resource.api.domain;

import lombok.Data;
import org.tuoyu.common.core.enums.PushSourceEnum;
import org.tuoyu.common.core.enums.PushTypeEnum;
import org.tuoyu.common.core.utils.StringUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 远程推送消息体
 *
 * @author Lion Li
 */
@Data
public class RemotePushPayLoad implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long messageId;

    private String type;

    private String source;

    private String message;

    private Object data;

    private String path;

    private Long timestamp;

    public static RemotePushPayLoad of(String type, String source, String message, Object data) {
        RemotePushPayLoad payload = new RemotePushPayLoad();
        payload.setType(StringUtils.defaultIfBlank(type, PushTypeEnum.MESSAGE.getType()));
        payload.setSource(StringUtils.defaultIfBlank(source, PushSourceEnum.BACKEND.getSource()));
        payload.setMessage(message);
        payload.setData(data);
        payload.setTimestamp(System.currentTimeMillis());
        return payload;
    }

    public static RemotePushPayLoad of(PushTypeEnum type, PushSourceEnum source, String message, Object data) {
        return of(
            type == null ? null : type.getType(),
            source == null ? null : source.getSource(),
            message,
            data
        );
    }

    public static RemotePushPayLoad of(PushTypeEnum type, PushSourceEnum source, String message, Object data, String path) {
        RemotePushPayLoad payload = of(type, source, message, data);
        payload.setPath(path);
        return payload;
    }
}
