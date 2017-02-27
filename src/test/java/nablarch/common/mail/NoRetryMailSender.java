package nablarch.common.mail;

import nablarch.core.db.statement.SqlRow;
import nablarch.core.log.app.FailureLogUtil;
import nablarch.core.util.annotation.Published;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.launcher.ProcessAbnormalEnd;

/**
 * {@link MailSender#handleException(SqlRow, ExecutionContext, MailRequestTable.MailRequest, MailConfig, Exception)} の実装を変更した {@link MailSender}。<p/>
 * 変更点
 * <ul>
 *     <li>{@link SendMailRetryableException}を送出しないで、{@link ProcessAbnormalEnd}を送出する。</li>
 * </ul>
 */
public class NoRetryMailSender extends MailSender {

    @Override
    @Published(tag = "architect")
    protected Result handleException(final SqlRow data, final ExecutionContext context,
            final MailRequestTable.MailRequest mailRequest, final MailConfig mailConfig, final Exception e) {
        FailureLogUtil.logError(e, data, mailConfig.getSendFailureCode(), mailRequest.getMailRequestId());
        updateToFailed(data, context);
        throw new ProcessAbnormalEnd(mailConfig.getAbnormalEndExitCode(), e, mailConfig.getSendFailureCode(),mailRequest.getMailRequestId());
    }
}
