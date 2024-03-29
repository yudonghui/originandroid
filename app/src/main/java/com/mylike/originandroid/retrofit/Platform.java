package com.mylike.originandroid.retrofit;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

class Platform {
    private static final Platform PLATFORM = findPlatform();

    static Platform get() {
        return PLATFORM;
    }

    private static Platform findPlatform() {
        try {
            Class.forName("android.os.Build");
            if (Build.VERSION.SDK_INT != 0) {
                return new Android();
            }
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("java.util.Optional");
            return new Java8();
        } catch (ClassNotFoundException ignored) {
        }
        return new Platform();
    }

    @Nullable Executor defaultCallbackExecutor() {
        return null;
    }

    List<? extends CallAdapter.Factory> defaultCallAdapterFactories(
            @Nullable Executor callbackExecutor) {
        if (callbackExecutor != null) {
            return singletonList(new ExecutorCallAdapterFactory(callbackExecutor));
        }
        return singletonList(DefaultCallAdapterFactory.INSTANCE);
    }

    int defaultCallAdapterFactoriesSize() {
        return 1;
    }

    List<? extends Converter.Factory> defaultConverterFactories() {
        return emptyList();
    }

    int defaultConverterFactoriesSize() {
        return 0;
    }

    boolean isDefaultMethod(Method method) {
        return false;
    }

    @Nullable Object invokeDefaultMethod(Method method, Class<?> declaringClass, Object object,
                                         @Nullable Object... args) throws Throwable {
        throw new UnsupportedOperationException();
    }

    @IgnoreJRERequirement // Only classloaded and used on Java 8.
    static class Java8 extends Platform {
        @Override boolean isDefaultMethod(Method method) {
            return method.isDefault();
        }

        @Override Object invokeDefaultMethod(Method method, Class<?> declaringClass, Object object,
                                             @Nullable Object... args) throws Throwable {
            // Because the service interface might not be public, we need to use a MethodHandle lookup
            // that ignores the visibility of the declaringClass.
            Constructor<Lookup> constructor = Lookup.class.getDeclaredConstructor(Class.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(declaringClass, -1 /* trusted */)
                    .unreflectSpecial(method, declaringClass)
                    .bindTo(object)
                    .invokeWithArguments(args);
        }

        @Override List<? extends CallAdapter.Factory> defaultCallAdapterFactories(
                @Nullable Executor callbackExecutor) {
            List<CallAdapter.Factory> factories = new ArrayList<>(2);
            factories.add(CompletableFutureCallAdapterFactory.INSTANCE);
            if (callbackExecutor != null) {
                factories.add(new ExecutorCallAdapterFactory(callbackExecutor));
            } else {
                factories.add(DefaultCallAdapterFactory.INSTANCE);
            }
            return unmodifiableList(factories);
        }

        @Override int defaultCallAdapterFactoriesSize() {
            return 2;
        }

        @Override List<? extends Converter.Factory> defaultConverterFactories() {
            return singletonList(OptionalConverterFactory.INSTANCE);
        }

        @Override int defaultConverterFactoriesSize() {
            return 1;
        }
    }

    static class Android extends Platform {
        @IgnoreJRERequirement // Guarded by API check.
        @Override boolean isDefaultMethod(Method method) {
            if (Build.VERSION.SDK_INT < 24) {
                return false;
            }
            return method.isDefault();
        }

        @Override public Executor defaultCallbackExecutor() {
            return new MainThreadExecutor();
        }

        @Override
        List<? extends CallAdapter.Factory> defaultCallAdapterFactories(
                @Nullable Executor callbackExecutor) {
            if (callbackExecutor == null) throw new AssertionError();
            ExecutorCallAdapterFactory executorFactory = new ExecutorCallAdapterFactory(callbackExecutor);
            return Build.VERSION.SDK_INT >= 24
                    ? asList(CompletableFutureCallAdapterFactory.INSTANCE, executorFactory)
                    : singletonList(executorFactory);
        }

        @Override int defaultCallAdapterFactoriesSize() {
            return Build.VERSION.SDK_INT >= 24 ? 2 : 1;
        }

        @Override List<? extends Converter.Factory> defaultConverterFactories() {
            return Build.VERSION.SDK_INT >= 24
                    ? singletonList(OptionalConverterFactory.INSTANCE)
                    : Collections.<Converter.Factory>emptyList();
        }

        @Override int defaultConverterFactoriesSize() {
            return Build.VERSION.SDK_INT >= 24 ? 1 : 0;
        }

        static class MainThreadExecutor implements Executor {
            private final Handler handler = new Handler(Looper.getMainLooper());

            @Override public void execute(Runnable r) {
                handler.post(r);
            }
        }
    }
}
