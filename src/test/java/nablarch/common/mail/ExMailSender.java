package nablarch.common.mail;

import nablarch.core.db.statement.SqlRow;
import nablarch.core.repository.SystemRepository;
import nablarch.core.util.annotation.Published;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.reader.DatabaseRecordReader;

/**
 * {@link MailSender#createReader} の実装を変更した {@link MailSender}
 */
public class ExMailSender extends MailSender {
    @Override
    @Published(tag = "architect")
    public DataReader<SqlRow> createReader(ExecutionContext ctx) {

        MailRequestTable mailRequestTable = SystemRepository.get("mailRequestTable");
        MailConfig mailConfig = SystemRepository.get("mailConfig");

        String mailSendPatternId = ctx.getSessionScopedVar("mailSendPatternId");

        int unsentRecordCount = mailRequestTable.getTargetCount(mailSendPatternId);

        writeLog(mailConfig.getMailRequestCountMessageId(), unsentRecordCount);

        DatabaseRecordReader reader = new DatabaseRecordReader();
        reader.setStatement(mailRequestTable.createReaderStatement(mailSendPatternId));

        return reader;
    }
}
