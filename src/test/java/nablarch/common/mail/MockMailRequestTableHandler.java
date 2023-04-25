package nablarch.common.mail;

import nablarch.core.repository.ObjectLoader;
import nablarch.core.repository.SystemRepository;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import org.mockito.MockedConstruction;

import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;

/**
 * {@link MailRequestTable}をモック化するハンドラ
 * <p>
 * {@code mockConstruction()}でモック化できるのは同じスレッド内に限られるので、
 * ハンドラキューの中でモック化を行えるようにするためにこのクラスが存在する。
 * </p>
 * 
 * @param <TData>    処理対象データ型
 * @param <TResult>  処理結果データ型
 */
public class MockMailRequestTableHandler<TData, TResult> implements Handler<TData, TResult> {
    private static Consumer<MailRequestTable> initializer;

    @Override
    public synchronized TResult handle(TData tData, ExecutionContext ctx) {
        final MailRequestTable original = SystemRepository.get("mailRequestTable");
        if (initializer != null) {
            final MailRequestTable spy = spy(original);
            initializer.accept(spy);
            overrideComponent(spy);
        }
        try {
            return ctx.handleNext(tData);
        } finally {
            overrideComponent(original);
        }
    }
    
    private void overrideComponent(MailRequestTable component) {
        SystemRepository.load(() -> Map.of("mailRequestTable", component));
    }

    public synchronized static void setInitializer(Consumer<MailRequestTable> initializer) {
        MockMailRequestTableHandler.initializer = initializer;
    }
}
