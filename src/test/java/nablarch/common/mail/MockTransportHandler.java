package nablarch.common.mail;

import jakarta.mail.Message;
import jakarta.mail.Transport;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;

/**
 * {@link Transport}をモック化するハンドラ
 * <p>
 * {@code mockConstruction()}でモック化できるのは同じスレッド内に限られるので、
 * ハンドラキューの中でモック化を行えるようにするためにこのクラスが存在する。
 * </p>
 *
 * @param <TData>    処理対象データ型
 * @param <TResult>  処理結果データ型
 */
public class MockTransportHandler<TData, TResult> implements Handler<TData, TResult> {
    
    private static Answer<?> answer;
    
    @Override
    public synchronized TResult handle(TData tData, ExecutionContext context) {
        if (answer == null) {
            return context.handleNext(tData);
        } else {
            try (final MockedStatic<Transport> mocked = Mockito.mockStatic(Transport.class)) {
                mocked.when(() -> Transport.send(any(Message.class))).then(answer);
                return context.handleNext(tData);
            }
        }
    }

    public synchronized static void setAnswer(Answer<?> answer) {
        MockTransportHandler.answer = answer;
    }
}
