package com.bumptech.glide.request;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.util.Pools;
import android.support.v7.content.res.AppCompatResources;
import android.util.Log;

import com.bumptech.glide.GlideContext;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.Engine;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.request.transition.TransitionFactory;
import com.bumptech.glide.util.LogTime;
import com.bumptech.glide.util.Synthetic;
import com.bumptech.glide.util.Util;
import com.bumptech.glide.util.pool.FactoryPools;
import com.bumptech.glide.util.pool.StateVerifier;

/**
 * A {@link Request} that loads a {@link com.bumptech.glide.load.engine.Resource} into a given
 * {@link Target}.
 * <p>
 * 一个用来加载一个{@link Resource}并存入一个给定的{@link Target}的{@link Request}的接口类
 *
 * @param <R> The type of the resource that will be transcoded from the loaded resource.
 */
public final class SingleRequest<R> implements Request,
        SizeReadyCallback,
        ResourceCallback,
        FactoryPools.Poolable {
    /**
     * Tag for logging internal events, not generally suitable for public use.
     */
    private static final String TAG = "Request";
    /**
     * Tag for logging externally useful events (request completion, timing etc).
     */
    private static final String GLIDE_TAG = "Glide";
    /**
     * 创建一个{@link SingleRequest}的对象池
     */
    private static final Pools.Pool<SingleRequest<?>> POOL = FactoryPools.simple(150,
            new FactoryPools.Factory<SingleRequest<?>>() {
                @Override
                public SingleRequest<?> create() {
                    return new SingleRequest<Object>();
                }
            });
    private boolean isCallingCallbacks;

    private enum Status {
        /**
         * Created but not yet running.
         */
        PENDING,
        /**
         * In the process of fetching media.
         */
        RUNNING,
        /**
         * Waiting for a callback given to the Target to be called to determine target dimensions.
         */
        WAITING_FOR_SIZE,
        /**
         * Finished loading media successfully.
         */
        COMPLETE,
        /**
         * Failed to load media, may be restarted.
         */
        FAILED,
        /**
         * Cancelled by the user, may not be restarted.
         */
        CANCELLED,
        /**
         * Cleared by the user with a placeholder set, may not be restarted.
         */
        CLEARED,
        /**
         * Temporarily paused by the system, may be restarted.
         */
        PAUSED,
    }

    private final String tag = String.valueOf(super.hashCode());
    private final StateVerifier stateVerifier = StateVerifier.newInstance();

    private RequestCoordinator requestCoordinator;
    private GlideContext glideContext;
    @Nullable
    private Object model;
    private Class<R> transcodeClass;
    /**
     * 默认情况下为{@link com.bumptech.glide.GlideBuilder#defaultRequestOptions}
     */
    private RequestOptions requestOptions;
    private int overrideWidth;
    private int overrideHeight;
    private Priority priority;
    private Target<R> target;
    /**
     * 这个需要用户在相应界面进行实现
     */
    private RequestListener<R> requestListener;
    private Engine engine;
    /**
     * @see {@link com.bumptech.glide.RequestBuilder#transitionOptions}
     */
    private TransitionFactory<? super R> animationFactory;
    private Resource<R> resource;
    private Engine.LoadStatus loadStatus;
    private long startTime;
    private Status status;
    private Drawable errorDrawable;
    private Drawable placeholderDrawable;
    private Drawable fallbackDrawable;
    private int width;
    private int height;
    private static boolean shouldCallAppCompatResources = true;

    /**
     * 从{@link #POOL}对象池中取一个对象作为返回值，若获取的对象为空，则创建一个新的
     * {@link SingleRequest}对象作为返回值；然后调用
     * {@link SingleRequest#init(GlideContext, Object, Class, RequestOptions,
     * int, int, Priority, Target, RequestListener, RequestCoordinator, Engine, TransitionFactory)}
     *
     * @param model           资源来源（如{@link java.io.InputStream}、{@link java.net.URL}、远程网址等）
     * @param requestOptions  Glide配置对象
     * @param overrideWidth   请求生成的图片宽度
     * @param overrideHeight  请求生成的图片高度
     * @param priority        请求的优先级
     * @param target          加载资源的容器
     * @param requestListener 供开发者自定义的图片加载过程做自定义处理的监听器
     */
    public static <R> SingleRequest<R> obtain(
            GlideContext glideContext,
            Object model,
            Class<R> transcodeClass,
            RequestOptions requestOptions,
            int overrideWidth,
            int overrideHeight,
            Priority priority,
            Target<R> target,
            RequestListener<R> requestListener,
            RequestCoordinator requestCoordinator,
            Engine engine,
            TransitionFactory<? super R> animationFactory) {
        @SuppressWarnings("unchecked") SingleRequest<R> request =
                (SingleRequest<R>) POOL.acquire();
        if (request == null) {
            request = new SingleRequest<>();
        }
        request.init(
                glideContext,
                model,
                transcodeClass,
                requestOptions,
                overrideWidth,
                overrideHeight,
                priority,
                target,
                requestListener,
                requestCoordinator,
                engine,
                animationFactory);
        return request;
    }

    @Synthetic
    SingleRequest() {
        // just create, instances are reused with recycle/init
    }

    private void init(
            GlideContext glideContext,
            Object model,
            Class<R> transcodeClass,
            RequestOptions requestOptions,
            int overrideWidth,
            int overrideHeight,
            Priority priority,
            Target<R> target,
            RequestListener<R> requestListener,
            RequestCoordinator requestCoordinator,
            Engine engine,
            TransitionFactory<? super R> animationFactory) {
        this.glideContext = glideContext;
        this.model = model;
        this.transcodeClass = transcodeClass;
        this.requestOptions = requestOptions;
        this.overrideWidth = overrideWidth;
        this.overrideHeight = overrideHeight;
        this.priority = priority;
        this.target = target;
        this.requestListener = requestListener;
        this.requestCoordinator = requestCoordinator;
        this.engine = engine;
        this.animationFactory = animationFactory;
        status = Status.PENDING;
    }

    @Override
    public StateVerifier getVerifier() {
        return stateVerifier;
    }

    @Override
    public void recycle() {
        assertNotCallingCallbacks();
        glideContext = null;
        model = null;
        transcodeClass = null;
        requestOptions = null;
        overrideWidth = -1;
        overrideHeight = -1;
        target = null;
        requestListener = null;
        requestCoordinator = null;
        animationFactory = null;
        loadStatus = null;
        errorDrawable = null;
        placeholderDrawable = null;
        fallbackDrawable = null;
        width = -1;
        height = -1;
        POOL.release(this);
    }

    /**
     * 一般的，调整请求图片的宽高值（即调用{@link #onSizeReady(int, int)}），然后开始加载图片
     * （即调用{@link Target#onLoadStarted(Drawable)}）
     */
    @Override
    public void begin() {
        assertNotCallingCallbacks();
        stateVerifier.throwIfRecycled();
        startTime = LogTime.getLogTime();
        if (model == null) {
            if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
                width = overrideWidth;
                height = overrideHeight;
            }
            // Only log at more verbose log levels if the user has set a fallback drawable, because
            // fallback Drawables indicate the user expects null models occasionally.
            int logLevel = getFallbackDrawable() == null ? Log.WARN : Log.DEBUG;
            onLoadFailed(new GlideException("Received null model"), logLevel);
            return;
        }

        if (status == Status.RUNNING) {
            throw new IllegalArgumentException("Cannot restart a running request");
        }

        // If we're restarted after we're complete (usually via something like a notifyDataSetChanged
        // that starts an identical request into the same Target or View), we can simply use the
        // resource and size we retrieved the last time around and skip obtaining a new size, starting a
        // new load etc. This does mean that users who want to restart a load because they expect that
        // the view size has changed will need to explicitly clear the View or Target before starting
        // the new load.
        if (status == Status.COMPLETE) {
            onResourceReady(resource, DataSource.MEMORY_CACHE);
            return;
        }

        // Restarts for requests that are neither complete nor running can be treated as new requests
        // and can run again from the beginning.

        status = Status.WAITING_FOR_SIZE;
        /**
         * 若宽高值不符合规范，则使用控件的宽高值
         * @see com.bumptech.glide.request.target.ViewTarget.SizeDeterminer#getSize(SizeReadyCallback)
         */
        if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
            onSizeReady(overrideWidth, overrideHeight);
        } else {
            target.getSize(this);
        }

        if ((status == Status.RUNNING || status == Status.WAITING_FOR_SIZE)
                && canNotifyStatusChanged()) {
            target.onLoadStarted(getPlaceholderDrawable());
        }
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            logV("finished run method in " + LogTime.getElapsedMillis(startTime));
        }
    }

    /**
     * Cancels the current load but does not release any resources held by the request and continues
     * to display the loaded resource if the load completed before the call to cancel.
     * <p>
     * <p> Cancelled requests can be restarted with a subsequent call to {@link #begin()}. </p>
     *
     * @see #clear()
     */
    void cancel() {
        assertNotCallingCallbacks();
        stateVerifier.throwIfRecycled();
        target.removeCallback(this);
        status = Status.CANCELLED;
        if (loadStatus != null) {
            loadStatus.cancel();
            loadStatus = null;
        }
    }

    // Avoids difficult to understand errors like #2413.
    private void assertNotCallingCallbacks() {
        if (isCallingCallbacks) {
            throw new IllegalStateException("You can't start or clear loads in RequestListener or"
                    + " Target callbacks. If you must do so, consider posting your into() or clear() calls"
                    + " to the main thread using a Handler instead.");
        }
    }

    /**
     * Cancels the current load if it is in progress, clears any resources held onto by the request
     * and replaces the loaded resource if the load completed with the placeholder.
     * <p>
     * <p> Cleared requests can be restarted with a subsequent call to {@link #begin()} </p>
     *
     * @see #cancel()
     */
    @Override
    public void clear() {
        Util.assertMainThread();
        assertNotCallingCallbacks();
        if (status == Status.CLEARED) {
            return;
        }
        cancel();
        // Resource must be released before canNotifyStatusChanged is called.
        if (resource != null) {
            releaseResource(resource);
        }
        if (canNotifyStatusChanged()) {
            target.onLoadCleared(getPlaceholderDrawable());
        }
        // Must be after cancel().
        status = Status.CLEARED;
    }

    @Override
    public boolean isPaused() {
        return status == Status.PAUSED;
    }

    @Override
    public void pause() {
        clear();
        status = Status.PAUSED;
    }

    private void releaseResource(Resource<?> resource) {
        engine.release(resource);
        this.resource = null;
    }

    @Override
    public boolean isRunning() {
        return status == Status.RUNNING || status == Status.WAITING_FOR_SIZE;
    }

    @Override
    public boolean isComplete() {
        return status == Status.COMPLETE;
    }

    @Override
    public boolean isResourceSet() {
        return isComplete();
    }

    @Override
    public boolean isCancelled() {
        return status == Status.CANCELLED || status == Status.CLEARED;
    }

    @Override
    public boolean isFailed() {
        return status == Status.FAILED;
    }

    private Drawable getErrorDrawable() {
        if (errorDrawable == null) {
            errorDrawable = requestOptions.getErrorPlaceholder();
            if (errorDrawable == null && requestOptions.getErrorId() > 0) {
                errorDrawable = loadDrawable(requestOptions.getErrorId());
            }
        }
        return errorDrawable;
    }

    /**
     * 返回图片正在加载时显示的图片
     */
    private Drawable getPlaceholderDrawable() {
        if (placeholderDrawable == null) {
            placeholderDrawable = requestOptions.getPlaceholderDrawable();
            if (placeholderDrawable == null && requestOptions.getPlaceholderId() > 0) {
                placeholderDrawable = loadDrawable(requestOptions.getPlaceholderId());
            }
        }
        return placeholderDrawable;
    }

    private Drawable getFallbackDrawable() {
        if (fallbackDrawable == null) {
            fallbackDrawable = requestOptions.getFallbackDrawable();
            if (fallbackDrawable == null && requestOptions.getFallbackId() > 0) {
                fallbackDrawable = loadDrawable(requestOptions.getFallbackId());
            }
        }
        return fallbackDrawable;
    }

    private Drawable loadDrawable(@DrawableRes int resourceId) {
        if (shouldCallAppCompatResources) {
            return loadDrawableV7(resourceId);
        } else {
            return loadDrawableBase(resourceId);
        }
    }

    /**
     * Tries to load the drawable thanks to AppCompatResources.<br>
     * This allows to parse VectorDrawables on legacy devices if the appcompat v7 is in the classpath.
     */
    private Drawable loadDrawableV7(@DrawableRes int resourceId) {
        try {
            return AppCompatResources.getDrawable(glideContext, resourceId);
        } catch (NoClassDefFoundError error) {
            shouldCallAppCompatResources = false;
            return loadDrawableBase(resourceId);
        }
    }

    private Drawable loadDrawableBase(@DrawableRes int resourceId) {
        Resources resources = glideContext.getResources();
        return ResourcesCompat.getDrawable(resources, resourceId, requestOptions.getTheme());
    }

    private void setErrorPlaceholder() {
        if (!canNotifyStatusChanged()) {
            return;
        }

        Drawable error = null;
        if (model == null) {
            error = getFallbackDrawable();
        }
        // Either the model isn't null, or there was no fallback drawable set.
        if (error == null) {
            error = getErrorDrawable();
        }
        // The model isn't null, no fallback drawable was set or no error drawable was set.
        if (error == null) {
            error = getPlaceholderDrawable();
        }
        target.onLoadFailed(error);
    }

    /**
     * A callback method that should never be invoked directly.
     * <p>
     * 一般的，根据{@link RequestOptions#getSizeMultiplier()}对宽高值传参进行调整，然后执行
     * {@link Engine#load(GlideContext, Object, Key, int, int, Class, Class, Priority,
     * DiskCacheStrategy, Map, boolean, Options, boolean, boolean, boolean, ResourceCallback)}
     */
    @Override
    public void onSizeReady(int width, int height) {
        stateVerifier.throwIfRecycled();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            logV("Got onSizeReady in " + LogTime.getElapsedMillis(startTime));
        }
        if (status != Status.WAITING_FOR_SIZE) {
            return;
        }
        status = Status.RUNNING;

        float sizeMultiplier = requestOptions.getSizeMultiplier();
        this.width = maybeApplySizeMultiplier(width, sizeMultiplier);
        this.height = maybeApplySizeMultiplier(height, sizeMultiplier);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            logV("finished setup for calling load in " + LogTime.getElapsedMillis(startTime));
        }
        loadStatus = engine.load(
                glideContext,
                model,
                requestOptions.getSignature(),
                this.width,
                this.height,
                requestOptions.getResourceClass(),
                transcodeClass,
                priority,
                requestOptions.getDiskCacheStrategy(),
                requestOptions.getTransformations(),
                requestOptions.isTransformationRequired(),
                requestOptions.isScaleOnlyOrNoTransform(),
                requestOptions.getOptions(),
                requestOptions.isMemoryCacheable(),
                requestOptions.getUseUnlimitedSourceGeneratorsPool(),
                requestOptions.getOnlyRetrieveFromCache(),
                this);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            logV("finished onSizeReady in " + LogTime.getElapsedMillis(startTime));
        }
    }

    private static int maybeApplySizeMultiplier(int size, float sizeMultiplier) {
        return size == Target.SIZE_ORIGINAL ? size : Math.round(sizeMultiplier * size);
    }

    private boolean canSetResource() {
        return requestCoordinator == null || requestCoordinator.canSetImage(this);
    }

    private boolean canNotifyStatusChanged() {
        return requestCoordinator == null || requestCoordinator.canNotifyStatusChanged(this);
    }

    /**
     * 判断是否是刚开始加载图片的状态，即{@link #status}不为{@link Status#COMPLETE}（也可以理解为显示
     * {@link RequestOptions#getPlaceholderDrawable()}的状态）
     */
    private boolean isFirstReadyResource() {
        return requestCoordinator == null || !requestCoordinator.isAnyResourceSet();
    }

    private void notifyLoadSuccess() {
        if (requestCoordinator != null) {
            requestCoordinator.onRequestSuccess(this);
        }
    }

    /**
     * A callback method that should never be invoked directly.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void onResourceReady(Resource<?> resource, DataSource dataSource) {
        stateVerifier.throwIfRecycled();
        loadStatus = null;
        if (resource == null) {
            GlideException exception = new GlideException("Expected to receive a Resource<R> with an "
                    + "object of " + transcodeClass + " inside, but instead got null.");
            onLoadFailed(exception);
            return;
        }

        Object received = resource.get();
        if (received == null || !transcodeClass.isAssignableFrom(received.getClass())) {
            releaseResource(resource);
            GlideException exception = new GlideException("Expected to receive an object of "
                    + transcodeClass + " but instead" + " got "
                    + (received != null ? received.getClass() : "") + "{" + received + "} inside" + " "
                    + "Resource{" + resource + "}."
                    + (received != null ? "" : " " + "To indicate failure return a null Resource "
                    + "object, rather than a Resource object containing null data."));
            onLoadFailed(exception);
            return;
        }

        if (!canSetResource()) {
            releaseResource(resource);
            // We can't put the status to complete before asking canSetResource().
            status = Status.COMPLETE;
            return;
        }

        onResourceReady((Resource<R>) resource, (R) received, dataSource);
    }

    /**
     * Internal {@link #onResourceReady(Resource, DataSource)} where arguments are known to be safe.
     *
     * @param resource original {@link Resource}, never <code>null</code>
     * @param result   object returned by {@link Resource#get()}, checked for type and never
     *                 <code>null</code>
     */
    private void onResourceReady(Resource<R> resource, R result, DataSource dataSource) {
        // We must call isFirstReadyResource before setting status.
        boolean isFirstResource = isFirstReadyResource();
        status = Status.COMPLETE;
        this.resource = resource;

        if (glideContext.getLogLevel() <= Log.DEBUG) {
            Log.d(GLIDE_TAG, "Finished loading " + result.getClass().getSimpleName() + " from "
                    + dataSource + " for " + model + " with size [" + width + "x" + height + "] in "
                    + LogTime.getElapsedMillis(startTime) + " ms");
        }

        isCallingCallbacks = true;
        try {
            if (requestListener == null
                    || !requestListener.onResourceReady(result, model, target, dataSource, isFirstResource)) {
                Transition<? super R> animation =
                        animationFactory.build(dataSource, isFirstResource);
                target.onResourceReady(result, animation);
            }
        } finally {
            isCallingCallbacks = false;
        }

        notifyLoadSuccess();
    }

    /**
     * A callback method that should never be invoked directly.
     */
    @Override
    public void onLoadFailed(GlideException e) {
        onLoadFailed(e, Log.WARN);
    }

    private void onLoadFailed(GlideException e, int maxLogLevel) {
        stateVerifier.throwIfRecycled();
        int logLevel = glideContext.getLogLevel();
        if (logLevel <= maxLogLevel) {
            Log.w(GLIDE_TAG, "Load failed for " + model + " with size [" + width + "x" + height + "]", e);
            if (logLevel <= Log.INFO) {
                e.logRootCauses(GLIDE_TAG);
            }
        }

        loadStatus = null;
        status = Status.FAILED;

        isCallingCallbacks = true;
        try {
            //TODO: what if this is a thumbnail request?
            if (requestListener == null
                    || !requestListener.onLoadFailed(e, model, target, isFirstReadyResource())) {
                setErrorPlaceholder();
            }
        } finally {
            isCallingCallbacks = false;
        }
    }

    @Override
    public boolean isEquivalentTo(Request o) {
        if (o instanceof SingleRequest) {
            SingleRequest that = (SingleRequest) o;
            return overrideWidth == that.overrideWidth
                    && overrideHeight == that.overrideHeight
                    && Util.bothModelsNullEquivalentOrEquals(model, that.model)
                    && transcodeClass.equals(that.transcodeClass)
                    && requestOptions.equals(that.requestOptions)
                    && priority == that.priority;
        }
        return false;
    }

    private void logV(String message) {
        Log.v(TAG, message + " this: " + tag);
    }
}