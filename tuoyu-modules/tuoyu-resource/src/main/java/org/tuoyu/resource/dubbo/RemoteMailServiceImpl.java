package org.tuoyu.resource.dubbo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mail.core.MailBuilder;
import org.tuoyu.resource.api.RemoteMailService;
import org.springframework.stereotype.Service;

/**
 * 邮件服务
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RemoteMailServiceImpl implements RemoteMailService {

    /**
     * 发送邮件
     *
     * @param to      接收人
     * @param subject 标题
     * @param text    内容
     */
    @Override
    public void send(String to, String subject, String text) throws ServiceException {
        MailBuilder.of().to(to).subject(subject).text(text).send();
    }

}
