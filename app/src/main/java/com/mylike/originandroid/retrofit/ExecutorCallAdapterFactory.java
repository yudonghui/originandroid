package com.mylike.originandroid.retrofit;


import androidx.annotation.Nullable;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.Executor;

final class ExecutorCallAdapterFactory extends CallAdapter.Factory {
    final Executor callbackExecutor;

    ExecutorCallAdapterFactory(Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    @Nullable
    public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
        if (getRawType(returnType) != Call.class) {
            return null;
        } else {
            final Type responseType = Utils.getCallResponseType(returnType);
            return new CallAdapter<Object, Call<?>>() {
                public Type responseType() {
                    return responseType;
                }

                public Call<Object> adapt(Call<Object> call) {
                    return new ExecutorCallAdapterFactory.ExecutorCallbackCall(ExecutorCallAdapterFactory.this.callbackExecutor, call);
                }
            };
        }
    }

    static final class ExecutorCallbackCall<T> implements Call<T> {
        final Executor callbackExecutor;
        final Call<T> delegate;

        ExecutorCallbackCall(Executor callbackExecutor, Call<T> delegate) {
            this.callbackExecutor = callbackExecutor;
            this.delegate = delegate;
        }

        public void enqueue(final Callback<T> callback) {
            Utils.checkNotNull(callback, "callback == null");
            this.delegate.enqueue(new Callback<T>() {
                public void onResponse(Call<T> call, final Response<T> response) {
                    //Android 平台中callbackExecutor是 MainThreadExecutor类。可以在这里看到retrofit是通过handler进行线程切换的。
                    ExecutorCallbackCall.this.callbackExecutor.execute(new Runnable() {
                        public void run() {
                            if (ExecutorCallbackCall.this.delegate.isCanceled()) {
                                callback.onFailure(ExecutorCallbackCall.this, new IOException("Canceled"));
                            } else {
                                callback.onResponse(ExecutorCallbackCall.this, response);
                            }

                        }
                    });
                }

                public void onFailure(Call<T> call, final Throwable t) {
                    ExecutorCallbackCall.this.callbackExecutor.execute(new Runnable() {
                        public void run() {
                            callback.onFailure(ExecutorCallbackCall.this, t);
                        }
                    });
                }
            });
        }

        public boolean isExecuted() {
            return this.delegate.isExecuted();
        }

        public Response<T> execute() throws IOException {
            return this.delegate.execute();
        }

        public void cancel() {
            this.delegate.cancel();
        }

        public boolean isCanceled() {
            return this.delegate.isCanceled();
        }

        public Call<T> clone() {
            return new ExecutorCallAdapterFactory.ExecutorCallbackCall(this.callbackExecutor, this.delegate.clone());
        }

        public Request request() {
            return this.delegate.request();
        }
    }
}
