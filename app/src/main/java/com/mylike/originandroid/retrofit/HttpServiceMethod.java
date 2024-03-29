package com.mylike.originandroid.retrofit;

/**
 * Created by ydh on 2021/6/30
 */

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import okhttp3.Response;
import okhttp3.ResponseBody;

/** Adapts an invocation of an interface method into an HTTP call. */
final class HttpServiceMethod<ResponseT, ReturnT> extends ServiceMethod<ReturnT> {
    /**
     *
     *
     */
    static <ResponseT, ReturnT> HttpServiceMethod<ResponseT, ReturnT> parseAnnotations(
            Retrofit retrofit, Method method, RequestFactory requestFactory) {
        //通过方法的返回类型选择一个CallAdapter对象。对应 .addCallAdapterFactory()中添加的返回值Call对象的转换器
        CallAdapter<ResponseT, ReturnT> callAdapter = createCallAdapter(retrofit, method);
        Type responseType = callAdapter.responseType();
        if (responseType == Response.class || responseType == okhttp3.Response.class) {
            throw methodError(method, "'"
                    + Utils.getRawType(responseType).getName()
                    + "' is not a valid response body type. Did you mean ResponseBody?");
        }
        if (requestFactory.httpMethod.equals("HEAD") && !Void.class.equals(responseType)) {
            throw methodError(method, "HEAD method must use Void as response type.");
        }
        //通过方法的返回类型选择一个Converte对象。对应 .addConverterFactory()中添加的转换器
        Converter<ResponseBody, ResponseT> responseConverter =
                createResponseConverter(retrofit, method, responseType);

        okhttp3.Call.Factory callFactory = retrofit.callFactory;
        return new HttpServiceMethod<>(requestFactory, callFactory, callAdapter, responseConverter);
    }

    private static <ResponseT, ReturnT> CallAdapter<ResponseT, ReturnT> createCallAdapter(
            Retrofit retrofit, Method method) {
        Type returnType = method.getGenericReturnType();
        Annotation[] annotations = method.getAnnotations();
        try {
            //noinspection unchecked
            return (CallAdapter<ResponseT, ReturnT>) retrofit.callAdapter(returnType, annotations);
        } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw methodError(method, e, "Unable to create call adapter for %s", returnType);
        }
    }

    private static <ResponseT> Converter<ResponseBody, ResponseT> createResponseConverter(
            Retrofit retrofit, Method method, Type responseType) {
        Annotation[] annotations = method.getAnnotations();
        try {
            return retrofit.responseBodyConverter(responseType, annotations);
        } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw methodError(method, e, "Unable to create converter for %s", responseType);
        }
    }

    private final RequestFactory requestFactory;
    private final okhttp3.Call.Factory callFactory;
    private final CallAdapter<ResponseT, ReturnT> callAdapter;
    private final Converter<ResponseBody, ResponseT> responseConverter;

    private HttpServiceMethod(RequestFactory requestFactory, okhttp3.Call.Factory callFactory,
                              CallAdapter<ResponseT, ReturnT> callAdapter,
                              Converter<ResponseBody, ResponseT> responseConverter) {
        this.requestFactory = requestFactory;
        this.callFactory = callFactory;
        this.callAdapter = callAdapter;
        this.responseConverter = responseConverter;
    }

    @Override ReturnT invoke(Object[] args) {
        return callAdapter.adapt(
                new OkHttpCall<>(requestFactory, args, callFactory, responseConverter));
    }
}
