package com.bumptech.glide.request;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Option;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.BitmapDrawableTransformation;
import com.bumptech.glide.load.resource.bitmap.BitmapEncoder;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CenterInside;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.load.resource.bitmap.Downsampler;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.bitmap.VideoBitmapDecoder;
import com.bumptech.glide.load.resource.gif.ByteBufferGifDecoder;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.load.resource.gif.GifDrawableTransformation;
import com.bumptech.glide.load.resource.gif.StreamGifDecoder;
import com.bumptech.glide.signature.EmptySignature;
import com.bumptech.glide.util.Preconditions;
import com.bumptech.glide.util.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Contains and exposes a variety of non type specific options that can be applied to a load in Glide.
 * <p>
 * Glide配置类
 * <p>
 * If {@link #lock()} has been called, this class will throw if any further mutations are
 * attempted. To unlock, use {@link #clone()}.
 *
 * @param <CHILD> The concrete and <em>final</em> subclass.
 */
public abstract class BaseRequestOptions<CHILD extends BaseRequestOptions<CHILD>> implements Cloneable {
    private static final int UNSET = -1;
    /**
     * {@code int}类型占用4个字节32位
     * <p>
     * 00000000 00000000 00000000 00000001 << 01 = 00000000 00000000 00000000 00000010 = 2
     * 00000000 00000000 00000000 00000001 << 02 = 00000000 00000000 00000000 00000100 = 4
     * 00000000 00000000 00000000 00000001 << 03 = 00000000 00000000 00000000 00001000 = 8
     * 00000000 00000000 00000000 00000001 << 04 = 00000000 00000000 00000000 00010000 = 16
     * 00000000 00000000 00000000 00000001 << 05 = 00000000 00000000 00000000 00100000 = 32
     * 00000000 00000000 00000000 00000001 << 06 = 00000000 00000000 00000000 01000000 = 64
     * 00000000 00000000 00000000 00000001 << 07 = 00000000 00000000 00000000 10000000 = 128
     * 00000000 00000000 00000000 00000001 << 08 = 00000000 00000000 00000001 00000000 = 256
     * 00000000 00000000 00000000 00000001 << 09 = 00000000 00000000 00000010 00000000 = 512
     * 00000000 00000000 00000000 00000001 << 10 = 00000000 00000000 00000100 00000000 = 1024
     * 00000000 00000000 00000000 00000001 << 11 = 00000000 00000000 00001000 00000000 = 2048
     * 00000000 00000000 00000000 00000001 << 12 = 00000000 00000000 00010000 00000000 = 4096
     * 00000000 00000000 00000000 00000001 << 13 = 00000000 00000000 00100000 00000000 = 8192
     * 00000000 00000000 00000000 00000001 << 14 = 00000000 00000000 01000000 00000000 = 16384
     * 00000000 00000000 00000000 00000001 << 15 = 00000000 00000000 10000000 00000000 = 32768
     * 00000000 00000000 00000000 00000001 << 16 = 00000000 00000001 00000000 00000000 = 65536
     * 00000000 00000000 00000000 00000001 << 17 = 00000000 00000010 00000000 00000000 = 131072
     * 00000000 00000000 00000000 00000001 << 18 = 00000000 00000100 00000000 00000000 = 262144
     * <p>
     * x << y 等价于x乘以2的y次方，x >> y 等价于x除以2的y次方
     * <p>
     * a |= b 等价于 a = a|b，用0（{@link #fields}的初始值）使用|（或）运算符，那么就相当于可以记录
     * 设置了哪些项（下面18个项每个代表一个设置项），比如设置了优先级项（用{@link #PRIORITY}表示）、
     * 图片加载失败项（用{@link #ERROR_PLACEHOLDER}表示）和磁盘缓存策略
     * （用{@link #DISK_CACHE_STRATEGY}表示）三个项，那么表示如下：
     * <p><pre>
     * 00000000 00000000 00000000 00000000 （{@link #fields}的初始值为0）
     * |
     * 00000000 00000000 00000000 00000100 （{@link #DISK_CACHE_STRATEGY}）
     * |
     * 00000000 00000000 00000000 00001000 （{@link #PRIORITY}）
     * |
     * 00000000 00000000 00000000 00010000 （{@link #ERROR_PLACEHOLDER}）
     * 计算结果是：
     * 00000000 00000000 00000000 00011100 （{@link #fields}现在的值）</pre>
     * <p>
     * a &= b 等价于 a = a&b；~a 表示取反，即0变1,1变0；a &= ~b 就可以实现取消设置项，
     * 比如取消了优先级项（用{@link #PRIORITY}表示）、图片加载失败项
     * （用{@link #ERROR_PLACEHOLDER}表示）和磁盘缓存策略（用{@link #DISK_CACHE_STRATEGY}表示）
     * 三个项，那么表示如下：
     * <p><pre>
     * 00000000 00000000 00000000 00011100 （{@link #fields}现在的值）
     * &
     * 11111111 11111111 11111111 11111011 （{@link #DISK_CACHE_STRATEGY}的取反值）
     * &
     * 11111111 11111111 11111111 11110111 （{@link #PRIORITY}的取反值）
     * &
     * 11111111 11111111 11111111 11101111 （{@link #ERROR_PLACEHOLDER}的取反值）
     * 计算结果是：
     * 00000000 00000000 00000000 00000000</pre>
     * <p>
     * 当然，由于每个设置项中都使用了{@link #clone()}，那么{@link #fields}也就标识设置了某个项
     */
    private static final int SIZE_MULTIPLIER = 1 << 1;
    private static final int DISK_CACHE_STRATEGY = 1 << 2;
    private static final int PRIORITY = 1 << 3;
    private static final int ERROR_PLACEHOLDER = 1 << 4;
    private static final int ERROR_ID = 1 << 5;
    private static final int PLACEHOLDER = 1 << 6;
    private static final int PLACEHOLDER_ID = 1 << 7;
    private static final int IS_CACHEABLE = 1 << 8;
    private static final int OVERRIDE = 1 << 9;
    private static final int SIGNATURE = 1 << 10;
    private static final int TRANSFORMATION = 1 << 11;
    private static final int RESOURCE_CLASS = 1 << 12;
    private static final int FALLBACK = 1 << 13;
    private static final int FALLBACK_ID = 1 << 14;
    private static final int THEME = 1 << 15;
    private static final int TRANSFORMATION_ALLOWED = 1 << 16;
    private static final int TRANSFORMATION_REQUIRED = 1 << 17;
    private static final int USE_UNLIMITED_SOURCE_GENERATORS_POOL = 1 << 18;
    private static final int ONLY_RETRIEVE_FROM_CACHE = 1 << 19;

    private int fields;

    private float sizeMultiplier = 1f;
    private DiskCacheStrategy diskCacheStrategy = DiskCacheStrategy.AUTOMATIC;
    private Priority priority = Priority.NORMAL;
    private Drawable errorPlaceholder;
    private int errorId;
    private Drawable placeholderDrawable;
    private int placeholderId;
    private boolean isCacheable = true;
    private int overrideHeight = UNSET;
    private int overrideWidth = UNSET;
    private Key signature = EmptySignature.obtain();
    private boolean isTransformationRequired;
    private boolean isTransformationAllowed = true;
    private Drawable fallbackDrawable;
    private int fallbackId;

    private Options options = new Options();
    private Map<Class<?>, Transformation<?>> transformations = new HashMap<>();
    private Class<?> resourceClass = Object.class;
    /**
     * 用来表示是否锁定，若为{@code true}，那么就不能对该设定类对象的任何选项值进行修改
     */
    private boolean isLocked;
    private Resources.Theme theme;
    private boolean isAutoCloneEnabled;
    private boolean useUnlimitedSourceGeneratorsPool;
    private boolean onlyRetrieveFromCache;

    /**
     * Applies a multiplier to the {@link com.bumptech.glide.request.target.Target}'s size before
     * loading the resource. Useful for loading thumbnails or trying to avoid loading huge resources
     * (particularly {@link Bitmap}s on devices with overly dense screens.
     * <p>
     * 在加载资源前给{@link com.bumptech.glide.request.target.Target}的大小设置一个乘数(0~1).
     * 当加载缩略图或避免加载过大的资源比较有用(尤其是在过于密集的屏幕上的Bitmap)
     *
     * @param sizeMultiplier The multiplier to apply to the
     *                       {@link com.bumptech.glide.request.target.Target}'s dimensions when
     *                       loading the resource.
     * @return This request builder.
     */
    public final CHILD sizeMultiplier(float sizeMultiplier) {
        if (isAutoCloneEnabled) {
            return clone().sizeMultiplier(sizeMultiplier);
        }

        if (sizeMultiplier < 0f || sizeMultiplier > 1f) {
            throw new IllegalArgumentException("sizeMultiplier must be between 0 and 1");
        }
        this.sizeMultiplier = sizeMultiplier;
        fields |= SIZE_MULTIPLIER;

        return selfOrThrowIfLocked();
    }

    public final CHILD useUnlimitedSourceGeneratorsPool(boolean flag) {
        if (isAutoCloneEnabled) {
            return clone().useUnlimitedSourceGeneratorsPool(flag);
        }

        this.useUnlimitedSourceGeneratorsPool = flag;
        fields |= USE_UNLIMITED_SOURCE_GENERATORS_POOL;

        return selfOrThrowIfLocked();
    }

    /**
     * If set to true, will only load an item if found in the cache, and will not fetch from source.
     */
    public final CHILD onlyRetrieveFromCache(boolean flag) {
        if (isAutoCloneEnabled) {
            return clone().onlyRetrieveFromCache(flag);
        }

        this.onlyRetrieveFromCache = flag;
        fields |= ONLY_RETRIEVE_FROM_CACHE;

        return selfOrThrowIfLocked();
    }

    /**
     * Sets the {@link DiskCacheStrategy} to use for this load.
     * <p>
     * 设置在加载时使用的{@link DiskCacheStrategy}
     * <p>
     * Defaults to {@link DiskCacheStrategy#AUTOMATIC}.
     * <p>
     * For most applications {@link DiskCacheStrategy#RESOURCE} is
     * ideal. Applications that use the same resource multiple times in multiple sizes and are willing
     * to trade off some speed and disk space in return for lower bandwidth usage may want to consider
     * using {@link DiskCacheStrategy#DATA} or {@link DiskCacheStrategy#ALL}.
     * <p>
     * 在大多数情况下设置为{@link DiskCacheStrategy#RESOURCE}是比较合理的.
     * 当多次使用同一个资源用于不同的尺寸并且宁愿消耗一些加载速度和磁盘空间来减少带宽使用的话，
     * 那么可以考虑设置为{@link DiskCacheStrategy#DATA}或{@link DiskCacheStrategy#ALL}
     *
     * @param strategy The strategy to use.
     * @return This request builder.
     */
    public final CHILD diskCacheStrategy(@NonNull DiskCacheStrategy strategy) {
        if (isAutoCloneEnabled) {
            return clone().diskCacheStrategy(strategy);
        }
        this.diskCacheStrategy = Preconditions.checkNotNull(strategy);
        fields |= DISK_CACHE_STRATEGY;

        return selfOrThrowIfLocked();
    }

    /**
     * Sets the priority for this load.
     * <p>
     * 设置加载的优先级
     *
     * @param priority A priority.
     * @return This request builder.
     */
    public final CHILD priority(@NonNull Priority priority) {
        if (isAutoCloneEnabled) {
            return clone().priority(priority);
        }

        this.priority = Preconditions.checkNotNull(priority);
        fields |= PRIORITY;

        return selfOrThrowIfLocked();
    }

    /**
     * Sets an {@link Drawable} to display while a resource is loading.
     * <p>
     * 设置一个用来显示的{@link Drawable}用来作为加载的资源
     *
     * @param drawable The drawable to display as a placeholder.
     * @return This request builder.
     */
    public final CHILD placeholder(@Nullable Drawable drawable) {
        if (isAutoCloneEnabled) {
            return clone().placeholder(drawable);
        }

        this.placeholderDrawable = drawable;
        fields |= PLACEHOLDER;

        return selfOrThrowIfLocked();
    }

    /**
     * Sets an Android resource id for a {@link Drawable} resource to display while a resource is loading.
     *
     * @param resourceId The id of the resource to use as a placeholder
     * @return This request builder.
     */
    public final CHILD placeholder(int resourceId) {
        if (isAutoCloneEnabled) {
            return clone().placeholder(resourceId);
        }

        this.placeholderId = resourceId;
        fields |= PLACEHOLDER_ID;

        return selfOrThrowIfLocked();
    }

    /**
     * Sets an {@link Drawable} to display if the model provided to
     * {@link com.bumptech.glide.RequestBuilder#load(Object)} is {@code null}.
     * <p>
     * 设置当{@link com.bumptech.glide.RequestBuilder#load(Object)}为空时需要显示的{@link Drawable}
     * <p>
     * If a fallback is not set, null models will cause the error drawable to be displayed. If the
     * error drawable is not set, the placeholder will be displayed.
     *
     * @param drawable The drawable to display as a placeholder.
     * @return This request builder.
     * @see #placeholder(Drawable)
     * @see #placeholder(int)
     */
    public final CHILD fallback(Drawable drawable) {
        if (isAutoCloneEnabled) {
            return clone().fallback(drawable);
        }

        this.fallbackDrawable = drawable;
        fields |= FALLBACK;

        return selfOrThrowIfLocked();
    }

    /**
     * Sets a resource to display if the model provided to
     * {@link com.bumptech.glide.RequestBuilder#load(Object)} is {@code null}.
     * <p>
     * If a fallback is not set, null models will cause the error drawable to be displayed. If
     * the error drawable is not set, the placeholder will be displayed.
     *
     * @param resourceId The id of the resource to use as a fallback.
     * @return This request builder.
     * @see #placeholder(Drawable)
     * @see #placeholder(int)
     */
    public final CHILD fallback(int resourceId) {
        if (isAutoCloneEnabled) {
            return clone().fallback(resourceId);
        }

        this.fallbackId = resourceId;
        fields |= FALLBACK_ID;

        return selfOrThrowIfLocked();
    }

    /**
     * Sets a {@link Drawable} to display if a load fails.
     * <p>
     * 设置一个加载失败时用来显示的{@link Drawable}
     *
     * @param drawable The drawable to display.
     * @return This request builder.
     */
    public final CHILD error(@Nullable Drawable drawable) {
        if (isAutoCloneEnabled) {
            return clone().error(drawable);
        }

        this.errorPlaceholder = drawable;
        fields |= ERROR_PLACEHOLDER;

        return selfOrThrowIfLocked();
    }

    /**
     * Sets a resource to display if a load fails.
     *
     * @param resourceId The id of the resource to use as a placeholder.
     * @return This request builder.
     */
    public final CHILD error(int resourceId) {
        if (isAutoCloneEnabled) {
            return clone().error(resourceId);
        }
        this.errorId = resourceId;
        fields |= ERROR_ID;

        return selfOrThrowIfLocked();
    }

    /**
     * Sets the {@link Resources.Theme} to apply when loading {@link Drawable}s
     * for resource ids provided via {@link #error(int)}, {@link #placeholder(int)}, and
     * {@link #fallback(Drawable)}.
     *
     * @param theme The theme to use when loading Drawables.
     * @return this request builder.
     */
    public final CHILD theme(Resources.Theme theme) {
        if (isAutoCloneEnabled) {
            return clone().theme(theme);
        }

        this.theme = theme;
        fields |= THEME;

        return selfOrThrowIfLocked();
    }

    /**
     * Allows the loaded resource to skip the memory cache.
     * <p>
     * 设置加载资源时是否跳过内存缓存
     * <p>
     * Note - this is not a guarantee. If a request is already pending for this resource and that
     * request is not also skipping the memory cache, the resource will be cached in memory.
     * <p>
     * 但是并不能完全保证(比如资源请求已经就绪)
     *
     * @param skip True to allow the resource to skip the memory cache.
     * @return This request builder.
     */
    public final CHILD skipMemoryCache(boolean skip) {
        if (isAutoCloneEnabled) {
            return clone().skipMemoryCache(true);
        }

        this.isCacheable = !skip;
        fields |= IS_CACHEABLE;

        return selfOrThrowIfLocked();
    }

    /**
     * Overrides the {@link com.bumptech.glide.request.target.Target}'s width and height with the
     * given values. This is useful for thumbnails, and should only be used for other cases when you
     * need a very specific image size.
     * <p>
     * 用给定值来设置{@link com.bumptech.glide.request.target.Target}的宽高值
     *
     * @param width  The width in pixels to use to load the resource.
     * @param height The height in pixels to use to load the resource.
     * @return This request builder.
     */
    public final CHILD override(int width, int height) {
        if (isAutoCloneEnabled) {
            return clone().override(width, height);
        }

        this.overrideWidth = width;
        this.overrideHeight = height;
        fields |= OVERRIDE;

        return selfOrThrowIfLocked();
    }

    /**
     * Overrides the {@link com.bumptech.glide.request.target.Target}'s width and height with the
     * given size.
     *
     * @param size The width and height to use.
     * @return This request builder.
     * @see #override(int, int)
     */
    public final CHILD override(int size) {
        return override(size, size);
    }

    /**
     * Sets some additional data to be mixed in to the memory and disk cache keys allowing the caller
     * more control over when cached data is invalidated.
     * <p>
     * 给无效的缓存数据添加一些额外信息
     * <p>
     * Note - The signature does not replace the cache key, it is purely additive.
     *
     * @param signature A unique non-null {@link Key} representing the current
     *                  state of the model that will be mixed in to the cache key.
     * @return This request builder.
     * @see com.bumptech.glide.signature.ObjectKey
     */
    public final CHILD signature(@NonNull Key signature) {
        if (isAutoCloneEnabled) {
            return clone().signature(signature);
        }

        this.signature = Preconditions.checkNotNull(signature);
        fields |= SIGNATURE;
        return selfOrThrowIfLocked();
    }

    /**
     * Returns a copy of this request builder with all of the options put so far on this builder.
     * <p>
     * This method returns a "deep" copy in that all non-immutable arguments are copied such that
     * changes to one builder will not affect the other builder. However, in addition to immutable
     * arguments, the current model is not copied copied so changes to the model will affect both
     * builders.
     * <p>
     * Even if this object was locked, the cloned object returned from this method will not be
     * locked.
     * <p>
     * 复制对象的一个副本，只是里面的大多数对象是浅拷贝
     */
    @SuppressWarnings("unchecked")
    @Override
    public final CHILD clone() {
        try {
            BaseRequestOptions<CHILD> result = (BaseRequestOptions<CHILD>) super.clone();
            /**
             * 由于{@link Object#clone()}默认浅拷贝，所以为了避免引用同一个变量地址，
             * 需要重新设置变量
             */
            result.options = new Options();
            result.options.putAll(options);
            result.transformations = new HashMap<>();
            result.transformations.putAll(transformations);
            result.isLocked = false;
            result.isAutoCloneEnabled = false;
            return (CHILD) result;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public final <T> CHILD set(@NonNull Option<T> option, @NonNull T value) {
        if (isAutoCloneEnabled) {
            return clone().set(option, value);
        }

        Preconditions.checkNotNull(option);
        Preconditions.checkNotNull(value);
        options.set(option, value);
        return selfOrThrowIfLocked();
    }

    public final CHILD decode(@NonNull Class<?> resourceClass) {
        if (isAutoCloneEnabled) {
            return clone().decode(resourceClass);
        }

        this.resourceClass = Preconditions.checkNotNull(resourceClass);
        fields |= RESOURCE_CLASS;
        return selfOrThrowIfLocked();
    }

    public final boolean isTransformationAllowed() {
        return isTransformationAllowed;
    }

    public final boolean isTransformationSet() {
        return isSet(TRANSFORMATION);
    }

    public final boolean isLocked() {
        return isLocked;
    }

    /**
     * Sets the value for key {@link BitmapEncoder#COMPRESSION_FORMAT}.
     * <p>
     * 设置图片编码格式(JPEG、PNG、WEBP)
     */
    public CHILD encodeFormat(@NonNull Bitmap.CompressFormat format) {
        return set(BitmapEncoder.COMPRESSION_FORMAT, Preconditions.checkNotNull(format));
    }

    /**
     * Sets the value for key {@link BitmapEncoder#COMPRESSION_QUALITY}.
     * <p>
     * 设置图片编码质量
     */
    public CHILD encodeQuality(int quality) {
        return set(BitmapEncoder.COMPRESSION_QUALITY, quality);
    }

    /**
     * Sets the {@link DecodeFormat} to use when decoding {@link Bitmap} objects using {@link Downsampler}.
     * <p>
     * 设置使用{@link Downsampler}解码{@link Bitmap}时使用的{@link DecodeFormat}
     * <p>
     * {@link DecodeFormat} is a request, not a requirement. It's possible the resource will be
     * decoded using a decoder that cannot control the format
     * ({@link android.media.MediaMetadataRetriever} for example), or that the decoder may choose to
     * ignore the requested format if it can't display the image (i.e. RGB_565 is requested, but the
     * image has alpha).
     */
    public CHILD format(@NonNull DecodeFormat format) {
        return set(Downsampler.DECODE_FORMAT, Preconditions.checkNotNull(format));
    }

    /**
     * Sets the time position of the frame to extract from a video.
     *
     * @param frameTimeMicros The time position in microseconds of the desired frame. If negative, the
     *                        Android framework implementation return a representative frame.
     */
    public CHILD frame(long frameTimeMicros) {
        return set(VideoBitmapDecoder.TARGET_FRAME, frameTimeMicros);
    }

    /**
     * Sets the {@link DownsampleStrategy} to use when decoding {@link Bitmap Bitmaps} using
     * {@link Downsampler}.
     */
    public CHILD downsample(@NonNull DownsampleStrategy strategy) {
        return set(Downsampler.DOWNSAMPLE_STRATEGY, Preconditions.checkNotNull(strategy));
    }

    /**
     * Applies {@link CenterCrop} to all default types, and ignores unknown types.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param context Any {@link Context}.
     * @see #optionalTransform(Class, Transformation)
     * @see #centerCrop(Context)
     */
    public CHILD optionalCenterCrop(Context context) {
        return optionalTransform(context, DownsampleStrategy.CENTER_OUTSIDE, new CenterCrop(context));
    }

    /**
     * Applies {@link CenterCrop} to all default types and
     * throws an exception if asked to transform an unknown type.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param context Any {@link Context}.
     * @see #transform(Class, Transformation)
     * @see #optionalCenterCrop(Context)
     */
    public CHILD centerCrop(Context context) {
        return transform(context, DownsampleStrategy.CENTER_OUTSIDE, new CenterCrop(context));
    }

    /**
     * Applies {@link FitCenter} to all default types, and
     * ignores unknown types.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param context Any {@link Context}.
     * @see #optionalTransform(Class, Transformation)
     * @see #fitCenter(Context)
     */
    public CHILD optionalFitCenter(Context context) {
        return optionalTransform(context, DownsampleStrategy.FIT_CENTER, new FitCenter(context));
    }

    /**
     * Applies {@link FitCenter} to all default types and
     * throws an exception if asked to transform an unknown type.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param context Any {@link Context}.
     * @see #transform(Class, Transformation)
     * @see #optionalFitCenter(Context)
     */
    public CHILD fitCenter(Context context) {
        return transform(context, DownsampleStrategy.FIT_CENTER, new FitCenter(context));
    }

    /**
     * Applies {@link CenterInside} to all default types, and ignores unknown types.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param context Any {@link Context}.
     * @see #optionalTransform(Class, Transformation)
     * @see #centerInside(Context) (android.content.Context)
     */
    public CHILD optionalCenterInside(Context context) {
        return optionalTransform(context, DownsampleStrategy.CENTER_INSIDE, new CenterInside(context));
    }

    /**
     * Applies {@link CenterInside} to all default types and throws an exception if asked to transform an unknown type.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param context Any {@link Context}.
     * @see #transform(Class, Transformation)
     * @see #optionalCenterInside(Context) (android.content.Context)
     */
    public CHILD centerInside(Context context) {
        return transform(context, DownsampleStrategy.CENTER_INSIDE, new CenterInside(context));
    }

    /**
     * Applies {@link CircleCrop} to all default types, and ignores unknown types.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param context Any {@link Context}.
     * @see #optionalTransform(Context, Transformation)
     * @see #circleCrop(Context)
     */
    public CHILD optionalCircleCrop(Context context) {
        return optionalTransform(context, DownsampleStrategy.CENTER_OUTSIDE, new CircleCrop(context));
    }

    /**
     * Applies {@link CircleCrop} to all default types and throws an exception if asked to transform
     * an unknown type.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param context Any {@link Context}.
     * @see #transform(Class, Transformation)
     * @see #optionalCenterCrop(Context)
     */
    public CHILD circleCrop(Context context) {
        return transform(context, DownsampleStrategy.CENTER_OUTSIDE, new CircleCrop(context));
    }

    final CHILD optionalTransform(Context context, DownsampleStrategy downsampleStrategy,
                                  Transformation<Bitmap> transformation) {
        if (isAutoCloneEnabled) {
            return clone().optionalTransform(context, downsampleStrategy, transformation);
        }

        downsample(downsampleStrategy);
        return optionalTransform(context, transformation);
    }

    final CHILD transform(Context context, DownsampleStrategy downsampleStrategy,
                          Transformation<Bitmap> transformation) {
        if (isAutoCloneEnabled) {
            return clone().transform(context, downsampleStrategy, transformation);
        }

        downsample(downsampleStrategy);
        return transform(context, transformation);
    }

    /**
     * Applies the given {@link Transformation} for
     * {@link Bitmap Bitmaps} to the default types ({@link Bitmap},
     * {@link BitmapDrawable}, and {@link GifDrawable})
     * and throws an exception if asked to transform an unknown type.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param context        Any {@link Context}.
     * @param transformation Any {@link Transformation} for
     *                       {@link Bitmap}s.
     * @see #optionalTransform(Context, Transformation)
     * @see #optionalTransform(Class, Transformation)
     */
    public CHILD transform(Context context, @NonNull Transformation<Bitmap> transformation) {
        if (isAutoCloneEnabled) {
            return clone().transform(context, transformation);
        }

        optionalTransform(context, transformation);
        isTransformationRequired = true;
        fields |= TRANSFORMATION_REQUIRED;
        return selfOrThrowIfLocked();
    }

    /**
     * Applies the given {@link Transformation} for
     * {@link Bitmap Bitmaps} to the default types ({@link Bitmap},
     * {@link BitmapDrawable}, and
     * {@link GifDrawable}) and ignores unknown types.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param context        Any {@link Context}.
     * @param transformation Any {@link Transformation} for
     *                       {@link Bitmap}s.
     * @see #transform(Context, Transformation)
     * @see #transform(Class, Transformation)
     */
    public CHILD optionalTransform(Context context, Transformation<Bitmap> transformation) {
        if (isAutoCloneEnabled) {
            return clone().optionalTransform(context, transformation);
        }

        optionalTransform(Bitmap.class, transformation);
        // TODO: remove BitmapDrawable decoder and this transformation.
        optionalTransform(BitmapDrawable.class,
                new BitmapDrawableTransformation(context, transformation));
        optionalTransform(GifDrawable.class, new GifDrawableTransformation(context, transformation));
        return selfOrThrowIfLocked();
    }

    /**
     * Applies the given {@link Transformation} for any decoded resource of
     * the given type and allows unknown resource types to be ignored.
     * <p>
     * Users can apply different transformations for each resource class. Applying a
     * {@link Transformation} for a resource type that already has a
     * {@link Transformation} will override the previous call.
     * <p>
     * If any calls are made to the non-optional transform methods, then attempting to transform
     * an unknown resource class will throw an exception. To allow unknown types, users must always
     * call the optional version of each method.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     * <p>
     * 给{@link #transformations}集合增加数据
     *
     * @param resourceClass  The type of resource to transform.
     * @param transformation The {@link Transformation} to apply.
     */
    public final <T> CHILD optionalTransform(Class<T> resourceClass,
                                             Transformation<T> transformation) {
        if (isAutoCloneEnabled) {
            return clone().optionalTransform(resourceClass, transformation);
        }

        Preconditions.checkNotNull(resourceClass);
        Preconditions.checkNotNull(transformation);
        transformations.put(resourceClass, transformation);
        fields |= TRANSFORMATION;
        isTransformationAllowed = true;
        fields |= TRANSFORMATION_ALLOWED;
        return selfOrThrowIfLocked();
    }

    /**
     * Applies the given {@link Transformation} for any decoded resource of
     * the given type and throws if asked to transform an unknown resource type.
     * <p>
     * This will override previous calls to {@link #dontTransform()}.
     *
     * @param resourceClass  The type of resource to transform.
     * @param transformation The {@link Transformation} to apply.
     * @see #optionalTransform(Class, Transformation)
     */
    public final <T> CHILD transform(Class<T> resourceClass, Transformation<T> transformation) {
        if (isAutoCloneEnabled) {
            return clone().transform(resourceClass, transformation);
        }

        optionalTransform(resourceClass, transformation);
        isTransformationRequired = true;
        fields |= TRANSFORMATION_REQUIRED;
        return selfOrThrowIfLocked();
    }

    /**
     * Removes all applied {@link Transformation Transformations} for all
     * resource classes and allows unknown resource types to be transformed without throwing an
     * exception.
     */
    public final CHILD dontTransform() {
        if (isAutoCloneEnabled) {
            return clone().dontTransform();
        }

        transformations.clear();
        fields &= ~TRANSFORMATION;
        isTransformationRequired = false;
        fields &= ~TRANSFORMATION_REQUIRED;
        isTransformationAllowed = false;
        fields |= TRANSFORMATION_ALLOWED;
        return selfOrThrowIfLocked();
    }

    /**
     * Disables resource decoders that return animated resources so any resource returned will be
     * static.
     * <p>
     * To disable transitions (fades etc) use
     * {@link com.bumptech.glide.TransitionOptions#dontTransition()}
     */
    public final CHILD dontAnimate() {
        if (isAutoCloneEnabled) {
            return clone().dontAnimate();
        }

        set(ByteBufferGifDecoder.DISABLE_ANIMATION, true);
        set(StreamGifDecoder.DISABLE_ANIMATION, true);
        return selfOrThrowIfLocked();
    }

    public final CHILD apply(BaseRequestOptions<?> other) {
        if (isAutoCloneEnabled) {
            return clone().apply(other);
        }

        /**
         * 若{@link #fields}为初始值0，那么返回{@code true}，否则返回{@code false}
         */
        if (isSet(other.fields, SIZE_MULTIPLIER)) {
            sizeMultiplier = other.sizeMultiplier;
        }
        if (isSet(other.fields, USE_UNLIMITED_SOURCE_GENERATORS_POOL)) {
            useUnlimitedSourceGeneratorsPool = other.useUnlimitedSourceGeneratorsPool;
        }
        if (isSet(other.fields, DISK_CACHE_STRATEGY)) {
            diskCacheStrategy = other.diskCacheStrategy;
        }
        if (isSet(other.fields, PRIORITY)) {
            priority = other.priority;
        }
        if (isSet(other.fields, ERROR_PLACEHOLDER)) {
            errorPlaceholder = other.errorPlaceholder;
        }
        if (isSet(other.fields, ERROR_ID)) {
            errorId = other.errorId;
        }
        if (isSet(other.fields, PLACEHOLDER)) {
            placeholderDrawable = other.placeholderDrawable;
        }
        if (isSet(other.fields, PLACEHOLDER_ID)) {
            placeholderId = other.placeholderId;
        }
        if (isSet(other.fields, IS_CACHEABLE)) {
            isCacheable = other.isCacheable;
        }
        if (isSet(other.fields, OVERRIDE)) {
            overrideWidth = other.overrideWidth;
            overrideHeight = other.overrideHeight;
        }
        if (isSet(other.fields, SIGNATURE)) {
            signature = other.signature;
        }
        if (isSet(other.fields, RESOURCE_CLASS)) {
            resourceClass = other.resourceClass;
        }
        if (isSet(other.fields, FALLBACK)) {
            fallbackDrawable = other.fallbackDrawable;
        }
        if (isSet(other.fields, FALLBACK_ID)) {
            fallbackId = other.fallbackId;
        }
        if (isSet(other.fields, THEME)) {
            theme = other.theme;
        }
        if (isSet(other.fields, TRANSFORMATION_ALLOWED)) {
            isTransformationAllowed = other.isTransformationAllowed;
        }
        if (isSet(other.fields, TRANSFORMATION_REQUIRED)) {
            isTransformationRequired = other.isTransformationRequired;
        }
        if (isSet(other.fields, TRANSFORMATION)) {
            transformations.putAll(other.transformations);
        }
        if (isSet(other.fields, ONLY_RETRIEVE_FROM_CACHE)) {
            onlyRetrieveFromCache = other.onlyRetrieveFromCache;
        }

        // Applying options with dontTransform() is expected to clear our transformations.
        if (!isTransformationAllowed) {
            transformations.clear();
            fields &= ~TRANSFORMATION;
            isTransformationRequired = false;
            fields &= ~TRANSFORMATION_REQUIRED;
        }

        fields |= other.fields;
        options.putAll(other.options);

        return selfOrThrowIfLocked();
    }

    /**
     * Throws if any further mutations are attempted.
     * <p>
     * Once locked, the only way to unlock is to use {@link #clone()}
     */
    @SuppressWarnings("unchecked")
    public final CHILD lock() {
        isLocked = true;
        // This is the only place we should not check locked.
        return (CHILD) this;
    }

    /**
     * Similar to {@link #lock()} except that mutations cause a {@link #clone()} operation to happen
     * before the mutation resulting in all methods returning a new Object and leaving the original
     * locked object unmodified.
     * <p>
     * Auto clone is not retained by cloned objects returned from mutations. The cloned objects
     * are mutable and are not locked.
     */
    public final CHILD autoLock() {
        if (isLocked && !isAutoCloneEnabled) {
            throw new IllegalStateException("You cannot auto lock an already locked options object"
                    + ", try clone() first");
        }
        isAutoCloneEnabled = true;
        return lock();
    }

    @SuppressWarnings("unchecked")
    private CHILD selfOrThrowIfLocked() {
        if (isLocked) {
            throw new IllegalStateException("You cannot modify locked RequestOptions, consider clone()");
        }
        return (CHILD) this;
    }

    public final Map<Class<?>, Transformation<?>> getTransformations() {
        return transformations;
    }

    public final boolean isTransformationRequired() {
        return isTransformationRequired;
    }

    public final Options getOptions() {
        return options;
    }

    public final Class<?> getResourceClass() {
        return resourceClass;
    }

    public final DiskCacheStrategy getDiskCacheStrategy() {
        return diskCacheStrategy;
    }

    public final Drawable getErrorPlaceholder() {
        return errorPlaceholder;
    }

    public final int getErrorId() {
        return errorId;
    }

    public final int getPlaceholderId() {
        return placeholderId;
    }

    public final Drawable getPlaceholderDrawable() {
        return placeholderDrawable;
    }

    public final int getFallbackId() {
        return fallbackId;
    }

    public final Drawable getFallbackDrawable() {
        return fallbackDrawable;
    }

    public final Resources.Theme getTheme() {
        return theme;
    }

    public final boolean isMemoryCacheable() {
        return isCacheable;
    }

    public final Key getSignature() {
        return signature;
    }

    public final boolean isPrioritySet() {
        return isSet(PRIORITY);
    }

    public final Priority getPriority() {
        return priority;
    }

    public final int getOverrideWidth() {
        return overrideWidth;
    }

    public final boolean isValidOverride() {
        return Util.isValidDimensions(overrideWidth, overrideHeight);
    }

    public final int getOverrideHeight() {
        return overrideHeight;
    }

    public final float getSizeMultiplier() {
        return sizeMultiplier;
    }

    private boolean isSet(int flag) {
        return isSet(fields, flag);
    }

    private static boolean isSet(int fields, int flag) {
        return (fields & flag) != 0;
    }

    public final boolean getUseUnlimitedSourceGeneratorsPool() {
        return useUnlimitedSourceGeneratorsPool;
    }

    public final boolean getOnlyRetrieveFromCache() {
        return onlyRetrieveFromCache;
    }
}