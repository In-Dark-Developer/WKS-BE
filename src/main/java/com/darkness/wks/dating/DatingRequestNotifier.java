package com.darkness.wks.dating;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * 소개팅 요청을 받은 사람에게 알림 메일을 보낸다.
 * <p>
 * 커밋 뒤에만 보낸다 — 롤백된 요청(중복·동시 요청 충돌)으로 메일이 나가면 받는 사람은 없는 요청을 찾게 된다.
 * SMTP 는 수 초까지 걸려서 요청 응답을 붙잡지 않도록 별도 스레드에서 보내고, 실패해도 요청은 이미 성사된 것이라
 * 로그만 남긴다. 메일에는 보낸 사람 정보를 넣지 않는다 — 잠긴 필드가 메일로 새지 않게 "요청이 왔다"는 사실만 알린다.
 */
@Slf4j
@Component
public class DatingRequestNotifier {

    // 개인 지메일로 발송해 학교 메일에서 스팸으로 분류되기 쉽다 — 반말·명령조 문구를 피하고 서비스명을 앞에 둔다
    private static final String SUBJECT = "[운명도 꿰어야 사랑이다] 새 소개팅 신청이 도착했어요";

    private final JavaMailSender mailSender;
    private final TaskExecutor taskExecutor;
    private final String mailFrom;

    public DatingRequestNotifier(JavaMailSender mailSender,
                                 @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                 @Value("${app.mail.from}") String mailFrom) {
        this.mailSender = mailSender;
        this.taskExecutor = taskExecutor;
        this.mailFrom = mailFrom;
    }

    public record DatingRequestSentEvent(UUID requestId, String recipientEmail) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestSent(DatingRequestSentEvent event) {
        taskExecutor.execute(() -> send(event));
    }

    void send(DatingRequestSentEvent event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(event.recipientEmail());
            helper.setSubject(SUBJECT);
            helper.setText("""
                    안녕하세요, 동국대 축제 '운명도 꿰어야 사랑이다'입니다.
                    누군가 회원님께 소개팅을 신청했어요.

                    사이트에 로그인해 받은 신청함에서 확인해 주세요.
                    """);
            mailSender.send(message);
        } catch (Exception exception) {
            // 예외 메시지·스택에 수신 주소가 실릴 수 있어(MailSendException 의 실패 주소 목록) 타입만 남긴다.
            // 받는 사람은 requestId 로 DB 에서 찾는다
            log.warn("dating request notification mail failed. requestId={}, cause={}", event.requestId(),
                    exception.getClass().getSimpleName());
        }
    }
}
