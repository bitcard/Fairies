package org.squirrelnest.fairies.thread;

import org.squirrelnest.fairies.thread.exception.LackInformException;
import org.squirrelnest.fairies.thread.inform.interfaces.Inform;

import java.util.concurrent.Callable;

/**
 * Created by Inoria on 2019/3/16.
 */
public abstract class CallableWithInform<T, I> implements Callable<T> {

    private Inform<I> inform;

    public abstract T originCall() throws Exception;

    @Override
    public T call() throws Exception {
        T result = originCall();
        if (inform == null) {
            throw new LackInformException();
        }
        inform.inform();
        return result;
    }

    public void setInform(Inform<I> inform) {
        this.inform = inform;
    }
}
