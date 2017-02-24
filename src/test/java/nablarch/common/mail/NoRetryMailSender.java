package nablarch.common.mail;

import javax.mail.MessagingException;

import nablarch.core.db.statement.ParameterizedSqlPStatement;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlRow;
import nablarch.core.repository.SystemRepository;
import nablarch.core.util.annotation.Published;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.handler.retry.RetryableException;
import nablarch.fw.reader.DatabaseRecordListener;
import nablarch.fw.reader.DatabaseRecordReader;

/**
 * {@link MailSender#checkAndThrowRetryableException} の実装を変更した {@link MailSender}。<p/>
 * 変更点
 * <ul>
 *     <li>{@link RetryableException}をスローしない。このため、{@link nablarch.fw.launcher.ProcessAbnormalEnd}をハンドラが返す。</li>
 * </ul>
 */
public class NoRetryMailSender extends MailSender {

    @Override
    @Published(tag = "architect")
    protected void checkAndThrowRetryableException(final SqlRow data, final ExecutionContext context,
            final MailRequestTable.MailRequest mailRequest, final MessagingException e) throws RetryableException {
        return;
    }
}
