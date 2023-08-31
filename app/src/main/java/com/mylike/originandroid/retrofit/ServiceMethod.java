package com.mylike.originandroid.retrofit;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Created by ydh on 2021/6/30
 */
abstract class ServiceMethod<T> {
    static <T> ServiceMethod<T> parseAnnotations(Retrofit retrofit, Method method) {
        RequestFactory requestFactory = RequestFactory.parseAnnotations(retrofit, method);

        Type returnType = method.getGenericReturnType();//表示方法的正式返回类型
        if (Utils.hasUnresolvableType(returnType)) {
            throw methodError(method,
                    "方法返回类型不能包含类型变量或通配符: %s", returnType);
        }
        if (returnType == void.class) {
            throw methodError(method, "方法不能返回void类型");
        }

        return HttpServiceMethod.parseAnnotations(retrofit, method, requestFactory);
    }

    abstract T invoke(Object[] args);
}
