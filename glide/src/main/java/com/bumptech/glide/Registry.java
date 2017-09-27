package com.bumptech.glide;

import android.support.v4.util.Pools.Pool;

import com.bumptech.glide.load.Encoder;
import com.bumptech.glide.load.ImageHeaderParser;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.ResourceEncoder;
import com.bumptech.glide.load.data.DataRewinder;
import com.bumptech.glide.load.data.DataRewinderRegistry;
import com.bumptech.glide.load.engine.DecodePath;
import com.bumptech.glide.load.engine.LoadPath;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.ModelLoaderRegistry;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;
import com.bumptech.glide.load.resource.transcode.TranscoderRegistry;
import com.bumptech.glide.provider.EncoderRegistry;
import com.bumptech.glide.provider.ImageHeaderParserRegistry;
import com.bumptech.glide.provider.LoadPathCache;
import com.bumptech.glide.provider.ModelToResourceClassCache;
import com.bumptech.glide.provider.ResourceDecoderRegistry;
import com.bumptech.glide.provider.ResourceEncoderRegistry;
import com.bumptech.glide.util.pool.FactoryPools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages component registration to extend or replace Glide's default loading, decoding, and
 * encoding logic.
 * <p>
 * 管理组件的注册
 */
public class Registry {
    private final ModelLoaderRegistry modelLoaderRegistry;
    private final EncoderRegistry encoderRegistry;
    private final ResourceDecoderRegistry decoderRegistry;
    private final ResourceEncoderRegistry resourceEncoderRegistry;
    private final DataRewinderRegistry dataRewinderRegistry;
    private final TranscoderRegistry transcoderRegistry;
    private final ImageHeaderParserRegistry imageHeaderParserRegistry;

    private final ModelToResourceClassCache modelToResourceClassCache =
            new ModelToResourceClassCache();
    private final LoadPathCache loadPathCache = new LoadPathCache();
    private final Pool<List<Exception>> exceptionListPool = FactoryPools.threadSafeList();

    public Registry() {
        this.modelLoaderRegistry = new ModelLoaderRegistry(exceptionListPool);
        this.encoderRegistry = new EncoderRegistry();
        this.decoderRegistry = new ResourceDecoderRegistry();
        this.resourceEncoderRegistry = new ResourceEncoderRegistry();
        this.dataRewinderRegistry = new DataRewinderRegistry();
        this.transcoderRegistry = new TranscoderRegistry();
        this.imageHeaderParserRegistry = new ImageHeaderParserRegistry();
    }

    /**
     * Registers the given {@link Encoder} for the given data class (InputStream, FileDescriptor etc).
     * <p>
     * <p>The {@link Encoder} will be used both for the exact data class and any subtypes. For
     * example, registering an {@link Encoder} for {@link java.io.InputStream} will result in the
     * {@link Encoder} being used for
     * {@link android.content.res.AssetFileDescriptor.AutoCloseInputStream},
     * {@link java.io.FileInputStream} and any other subclass.
     * <p>
     * <p>If multiple {@link Encoder}s are registered for the same type or super type, the
     * {@link Encoder} that is registered first will be used.
     *
     * @deprecated Use the equivalent {@link #append(Class, Class, ModelLoaderFactory)} method
     * instead.
     */
    @Deprecated
    public <Data> Registry register(Class<Data> dataClass, Encoder<Data> encoder) {
        return append(dataClass, encoder);
    }

    /**
     * Appends the given {@link Encoder} onto the list of available {@link Encoder}s so that it is
     * attempted after all earlier and default {@link Encoder}s for the given data class.
     * <p>
     * <p>The {@link Encoder} will be used both for the exact data class and any subtypes. For
     * example, registering an {@link Encoder} for {@link java.io.InputStream} will result in the
     * {@link Encoder} being used for
     * {@link android.content.res.AssetFileDescriptor.AutoCloseInputStream},
     * {@link java.io.FileInputStream} and any other subclass.
     * <p>
     * <p>If multiple {@link Encoder}s are registered for the same type or super type, the
     * {@link Encoder} that is registered first will be used.
     *
     * @see #prepend(Class, Encoder)
     */
    public <Data> Registry append(Class<Data> dataClass, Encoder<Data> encoder) {
        encoderRegistry.append(dataClass, encoder);
        return this;
    }

    /**
     * Prepends the given {@link Encoder} into the list of available {@link Encoder}s
     * so that it is attempted before all later and default {@link Encoder}s for the given
     * data class.
     * <p>
     * <p>This method allows you to replace the default {@link Encoder} because it ensures
     * the registered {@link Encoder} will run first. If multiple {@link Encoder}s are registered for
     * the same type or super type, the {@link Encoder} that is registered first will be used.
     *
     * @see #append(Class, Encoder)
     */
    public <Data> Registry prepend(Class<Data> dataClass, Encoder<Data> encoder) {
        encoderRegistry.prepend(dataClass, encoder);
        return this;
    }

    /**
     * Appends the given {@link ResourceDecoder} onto the list of available {@link ResourceDecoder}s
     * allowing it to be used if all earlier and default {@link ResourceDecoder}s for the given types
     * fail (or if none are present).
     * <p>
     * <p>If you're attempting to replace an existing {@link ResourceDecoder} or would like to ensure
     * that your {@link ResourceDecoder} gets the chance to run before an existing
     * {@link ResourceDecoder}, use {@link #prepend(Class, Class, ResourceDecoder)}. This method is
     * best for new types of resources and data or as a way to add an additional fallback decoder
     * for an existing type of data.
     *
     * @param dataClass     The data that will be decoded from
     *                      ({@link java.io.InputStream}, {@link java.io.FileDescriptor} etc).
     * @param resourceClass The resource that will be decoded to ({@link android.graphics.Bitmap},
     *                      {@link com.bumptech.glide.load.resource.gif.GifDrawable} etc).
     * @param decoder       The {@link ResourceDecoder} to register.
     * @see #prepend(Class, Class, ResourceDecoder)
     */
    public <Data, TResource> Registry append(
            Class<Data> dataClass,
            Class<TResource> resourceClass,
            ResourceDecoder<Data, TResource> decoder) {
        decoderRegistry.append(decoder, dataClass, resourceClass);
        return this;
    }

    /**
     * 将该条数据插入到ArrayList的首位
     */
    /**
     * Prepends the given {@link ResourceDecoder} into the list of available {@link ResourceDecoder}s
     * so that it is attempted before all later and default {@link ResourceDecoder}s for the given
     * types.
     * <p>
     * <p>This method allows you to replace the default {@link ResourceDecoder} because it ensures
     * the registered {@link ResourceDecoder} will run first. You can use the
     * {@link ResourceDecoder#handles(Object, Options)} to fall back to the default
     * {@link ResourceDecoder}s if you only want to change the default functionality for certain
     * types of data.
     *
     * @param dataClass     The data that will be decoded from
     *                      ({@link java.io.InputStream}, {@link java.io.FileDescriptor} etc).
     * @param resourceClass The resource that will be decoded to ({@link android.graphics.Bitmap},
     *                      {@link com.bumptech.glide.load.resource.gif.GifDrawable} etc).
     * @param decoder       The {@link ResourceDecoder} to register.
     * @see #append(Class, Class, ResourceDecoder)
     */
    public <Data, TResource> Registry prepend(
            Class<Data> dataClass,
            Class<TResource> resourceClass,
            ResourceDecoder<Data, TResource> decoder) {
        decoderRegistry.prepend(decoder, dataClass, resourceClass);
        return this;
    }

    /**
     * Appends the given {@link ResourceEncoder} into the list of available {@link ResourceEncoder}s
     * so that it is attempted after all earlier and default {@link ResourceEncoder}s for the given
     * data type.
     * <p>
     * <p>The {@link ResourceEncoder} will be used both for the exact resource class and any subtypes.
     * For example, registering an {@link ResourceEncoder} for
     * {@link android.graphics.drawable.Drawable} (not recommended) will result in the
     * {@link ResourceEncoder} being used for {@link android.graphics.drawable.BitmapDrawable} and
     * {@link com.bumptech.glide.load.resource.gif.GifDrawable} and any other subclass.
     * <p>
     * <p>If multiple {@link ResourceEncoder}s are registered for the same type or super type, the
     * {@link ResourceEncoder} that is registered first will be used.
     *
     * @deprecated Use the equivalent {@link #append(Class, ResourceEncoder)} method instead.
     */
    @Deprecated
    public <TResource> Registry register(
            Class<TResource> resourceClass, ResourceEncoder<TResource> encoder) {
        return append(resourceClass, encoder);
    }

    /**
     * Appends the given {@link ResourceEncoder} into the list of available {@link ResourceEncoder}s
     * so that it is attempted after all earlier and default {@link ResourceEncoder}s for the given
     * data type.
     * <p>
     * <p>The {@link ResourceEncoder} will be used both for the exact resource class and any subtypes.
     * For example, registering an {@link ResourceEncoder} for
     * {@link android.graphics.drawable.Drawable} (not recommended) will result in the
     * {@link ResourceEncoder} being used for {@link android.graphics.drawable.BitmapDrawable} and
     * {@link com.bumptech.glide.load.resource.gif.GifDrawable} and any other subclass.
     * <p>
     * <p>If multiple {@link ResourceEncoder}s are registered for the same type or super type, the
     * {@link ResourceEncoder} that is registered first will be used.
     *
     * @see #prepend(Class, ResourceEncoder)
     */
    public <TResource> Registry append(
            Class<TResource> resourceClass, ResourceEncoder<TResource> encoder) {
        resourceEncoderRegistry.append(resourceClass, encoder);
        return this;
    }

    /**
     * Prepends the given {@link ResourceEncoder} into the list of available {@link ResourceEncoder}s
     * so that it is attempted before all later and default {@link ResourceEncoder}s for the given
     * data type.
     * <p>
     * <p>This method allows you to replace the default {@link ResourceEncoder} because it ensures
     * the registered {@link ResourceEncoder} will run first. If multiple {@link ResourceEncoder}s are
     * registered for the same type or super type, the {@link ResourceEncoder} that is registered
     * first will be used.
     *
     * @see #append(Class, ResourceEncoder)
     */
    public <TResource> Registry prepend(
            Class<TResource> resourceClass, ResourceEncoder<TResource> encoder) {
        resourceEncoderRegistry.prepend(resourceClass, encoder);
        return this;
    }

    /**
     * Registers a new {@link com.bumptech.glide.load.data.DataRewinder.Factory} to handle a
     * non-default data type that can be rewind to allow for efficient reads of file headers.
     */
    public Registry register(DataRewinder.Factory factory) {
        dataRewinderRegistry.register(factory);
        return this;
    }

    /**
     * Registers the given {@link ResourceTranscoder} to convert from the given resource {@link Class}
     * to the given transcode {@link Class}.
     *
     * @param resourceClass  The class that will be transcoded from (e.g.
     *                       {@link android.graphics.Bitmap}).
     * @param transcodeClass The class that will be transcoded to (e.g.
     *                       {@link android.graphics.drawable.BitmapDrawable}).
     * @param transcoder     The {@link ResourceTranscoder} to register.
     */
    public <TResource, Transcode> Registry register(Class<TResource> resourceClass,
                                                    Class<Transcode> transcodeClass, ResourceTranscoder<TResource, Transcode> transcoder) {
        transcoderRegistry.register(resourceClass, transcodeClass, transcoder);
        return this;
    }

    /**
     * Registers a new {@link ImageHeaderParser} that can obtain some basic metadata from an image
     * header (orientation, type etc).
     */
    public Registry register(ImageHeaderParser parser) {
        imageHeaderParserRegistry.add(parser);
        return this;
    }

    /**
     * Appends a new {@link ModelLoaderFactory} onto the end of the existing set so that the
     * constructed {@link ModelLoader} will be tried after all default and previously registered
     * {@link ModelLoader}s for the given model and data classes.
     * <p>
     * <p>If you're attempting to replace an existing {@link ModelLoader}, use
     * {@link #prepend(Class, Class, ModelLoaderFactory)}. This method is best for new types of models
     * and/or data or as a way to add an additional fallback loader for an existing type of
     * model/data.
     * <p>
     * <p>If multiple {@link ModelLoaderFactory}s are registered for the same model and/or data
     * classes, the {@link ModelLoader}s they produce will be attempted in the order the
     * {@link ModelLoaderFactory}s were registered. Only if all {@link ModelLoader}s fail will the
     * entire request fail.
     *
     * @param modelClass The model class (e.g. URL, file path).
     * @param dataClass  the data class (e.g. {@link java.io.InputStream},
     *                   {@link java.io.FileDescriptor}).
     * @see #prepend(Class, Class, ModelLoaderFactory)
     * @see #replace(Class, Class, ModelLoaderFactory)
     */
    public <Model, Data> Registry append(Class<Model> modelClass, Class<Data> dataClass,
                                         ModelLoaderFactory<Model, Data> factory) {
        modelLoaderRegistry.append(modelClass, dataClass, factory);
        return this;
    }

    /**
     * Prepends a new {@link ModelLoaderFactory} onto the beginning of the existing set so that the
     * constructed {@link ModelLoader} will be tried before all default and previously registered
     * {@link ModelLoader}s for the given model and data classes.
     * <p>
     * <p>If you're attempting to add additional functionality or add a backup that should run only
     * after the default {@link ModelLoader}s run, use
     * {@link #append(Class, Class, ModelLoaderFactory)}. This method is best for adding an additional
     * case to Glide's existing functionality that should run first. This method will still run
     * Glide's default {@link ModelLoader}s if the prepended {@link ModelLoader}s fail.
     * <p>
     * <p>If multiple {@link ModelLoaderFactory}s are registered for the same model and/or data
     * classes, the {@link ModelLoader}s they produce will be attempted in the order the
     * {@link ModelLoaderFactory}s were registered. Only if all {@link ModelLoader}s fail will the
     * entire request fail.
     *
     * @param modelClass The model class (e.g. URL, file path).
     * @param dataClass  the data class (e.g. {@link java.io.InputStream},
     *                   {@link java.io.FileDescriptor}).
     * @see #append(Class, Class, ModelLoaderFactory)
     * @see #replace(Class, Class, ModelLoaderFactory)
     */
    public <Model, Data> Registry prepend(Class<Model> modelClass, Class<Data> dataClass,
                                          ModelLoaderFactory<Model, Data> factory) {
        modelLoaderRegistry.prepend(modelClass, dataClass, factory);
        return this;
    }

    /**
     * Removes all default and previously registered {@link ModelLoaderFactory}s for the given data
     * and model class and replaces all of them with the single {@link ModelLoader} provided.
     * <p>
     * <p>If you're attempting to add additional functionality or add a backup that should run only
     * after the default {@link ModelLoader}s run, use
     * {@link #append(Class, Class, ModelLoaderFactory)}. This method should be used only when you
     * want to ensure that Glide's default {@link ModelLoader}s do not run.
     * <p>
     * <p>One good use case for this method is when you want to replace Glide's default networking
     * library with your OkHttp, Volley, or your own implementation. Using
     * {@link #prepend(Class, Class, ModelLoaderFactory)} or
     * {@link #append(Class, Class, ModelLoaderFactory)} may still allow Glide's default networking
     * library to run in some cases. Using this method will ensure that only your networking library
     * will run and that the request will fail otherwise.
     *
     * @param modelClass The model class (e.g. URL, file path).
     * @param dataClass  the data class (e.g. {@link java.io.InputStream},
     *                   {@link java.io.FileDescriptor}).
     * @see #prepend(Class, Class, ModelLoaderFactory)
     * @see #append(Class, Class, ModelLoaderFactory)
     */
    public <Model, Data> Registry replace(Class<Model> modelClass, Class<Data> dataClass,
                                          ModelLoaderFactory<Model, Data> factory) {
        modelLoaderRegistry.replace(modelClass, dataClass, factory);
        return this;
    }

    public <Data, TResource, Transcode> LoadPath<Data, TResource, Transcode> getLoadPath(
            Class<Data> dataClass, Class<TResource> resourceClass, Class<Transcode> transcodeClass) {
        LoadPath<Data, TResource, Transcode> result =
                loadPathCache.get(dataClass, resourceClass, transcodeClass);
        if (result == null && !loadPathCache.contains(dataClass, resourceClass, transcodeClass)) {
            List<DecodePath<Data, TResource, Transcode>> decodePaths =
                    getDecodePaths(dataClass, resourceClass, transcodeClass);
            // It's possible there is no way to decode or transcode to the desired types from a given
            // data class.
            if (decodePaths.isEmpty()) {
                result = null;
            } else {
                result = new LoadPath<>(dataClass, resourceClass, transcodeClass, decodePaths,
                        exceptionListPool);
            }
            loadPathCache.put(dataClass, resourceClass, transcodeClass, result);
        }
        return result;
    }

    private <Data, TResource, Transcode> List<DecodePath<Data, TResource, Transcode>> getDecodePaths(
            Class<Data> dataClass, Class<TResource> resourceClass, Class<Transcode> transcodeClass) {

        List<DecodePath<Data, TResource, Transcode>> decodePaths = new ArrayList<>();
        List<Class<TResource>> registeredResourceClasses =
                decoderRegistry.getResourceClasses(dataClass, resourceClass);

        for (Class<TResource> registeredResourceClass : registeredResourceClasses) {
            List<Class<Transcode>> registeredTranscodeClasses =
                    transcoderRegistry.getTranscodeClasses(registeredResourceClass, transcodeClass);

            for (Class<Transcode> registeredTranscodeClass : registeredTranscodeClasses) {

                List<ResourceDecoder<Data, TResource>> decoders =
                        decoderRegistry.getDecoders(dataClass, registeredResourceClass);
                ResourceTranscoder<TResource, Transcode> transcoder =
                        transcoderRegistry.get(registeredResourceClass, registeredTranscodeClass);
                decodePaths.add(new DecodePath<>(dataClass, registeredResourceClass,
                        registeredTranscodeClass, decoders, transcoder, exceptionListPool));
            }
        }
        return decodePaths;
    }

    /**
     * {@link MultiModelLoaderFactory.Entry#dataClass}和{@link ResourceDecoderRegistry.Entry}两个
     * 集合的交集再去除掉{@link TranscoderRegistry.Entry}集合中符合传参的项的集合
     * <p>
     * 返回值为{@link TranscoderRegistry#transcoders}中指定的
     * {@link TranscoderRegistry.Entry#fromClass}的集合
     */
    public <Model, TResource, Transcode> List<Class<?>> getRegisteredResourceClasses(
            Class<Model> modelClass, Class<TResource> resourceClass, Class<Transcode> transcodeClass) {
        List<Class<?>> result = modelToResourceClassCache.get(modelClass, resourceClass);

        if (result == null) {
            result = new ArrayList<>();
            /**
             * 返回{@link MultiModelLoaderFactory#entries}集合中
             * {@link MultiModelLoaderFactory.Entry#modelClass}为传参中的{@code modelClass}的
             * {@link MultiModelLoaderFactory.Entry#dataClass}的集合
             */
            List<Class<?>> dataClasses = modelLoaderRegistry.getDataClasses(modelClass);
            for (Class<?> dataClass : dataClasses) {
                List<? extends Class<?>> registeredResourceClasses =
                        decoderRegistry.getResourceClasses(dataClass, resourceClass);
                for (Class<?> registeredResourceClass : registeredResourceClasses) {
                    List<Class<Transcode>> registeredTranscodeClasses = transcoderRegistry
                            .getTranscodeClasses(registeredResourceClass, transcodeClass);
                    if (!registeredTranscodeClasses.isEmpty() && !result.contains(registeredResourceClass)) {
                        result.add(registeredResourceClass);
                    }
                }
            }
            modelToResourceClassCache.put(modelClass, resourceClass,
                    Collections.unmodifiableList(result));
        }

        return result;
    }

    public boolean isResourceEncoderAvailable(Resource<?> resource) {
        return resourceEncoderRegistry.get(resource.getResourceClass()) != null;
    }

    /**
     * 返回{@link ResourceEncoderRegistry#encoders}集合中符合参数的项
     */
    public <X> ResourceEncoder<X> getResultEncoder(Resource<X> resource)
            throws NoResultEncoderAvailableException {
        ResourceEncoder<X> resourceEncoder = resourceEncoderRegistry.get(resource.getResourceClass());
        if (resourceEncoder != null) {
            return resourceEncoder;
        }
        throw new NoResultEncoderAvailableException(resource.getResourceClass());
    }

    /**
     * 返回{@link EncoderRegistry#encoders}集合中符合参数的项
     */
    @SuppressWarnings("unchecked")
    public <X> Encoder<X> getSourceEncoder(X data) throws NoSourceEncoderAvailableException {
        Encoder<X> encoder = encoderRegistry.getEncoder((Class<X>) data.getClass());
        if (encoder != null) {
            return encoder;
        }
        throw new NoSourceEncoderAvailableException(data.getClass());
    }

    public <X> DataRewinder<X> getRewinder(X data) {
        return dataRewinderRegistry.build(data);
    }

    /**
     * 返回集合中实现了{@link ModelLoader}接口的类中{@link ModelLoader#handles(Object)}
     * 返回为{@code true}且未使用的所有数据项的列表，并且调用传参中实现
     * {@link ModelLoaderFactory}的对象的{@link ModelLoaderFactory#build(MultiModelLoaderFactory)}
     *
     * @param model 即请求图片的来源（如一个网址字符串、文件等），
     *              和{@link MultiModelLoaderFactory.Entry#modelClass}对应起来
     * @return 返回和传参基类相同 {@link MultiModelLoaderFactory.Entry#modelClass}所对应的
     * {@link MultiModelLoaderFactory.Entry#factory}所调用
     * {@link ModelLoaderFactory#build(MultiModelLoaderFactory)}创建的实例
     */
    public <Model> List<ModelLoader<Model, ?>> getModelLoaders(Model model) {
        List<ModelLoader<Model, ?>> result = modelLoaderRegistry.getModelLoaders(model);
        if (result.isEmpty()) {
            throw new NoModelLoaderAvailableException(model);
        }
        return result;
    }

    public List<ImageHeaderParser> getImageHeaderParsers() {
        List<ImageHeaderParser> result = imageHeaderParserRegistry.getParsers();
        if (result.isEmpty()) {
            throw new NoImageHeaderParserException();
        }
        return result;
    }

    /**
     * Thrown when no {@link com.bumptech.glide.load.model.ModelLoader} is registered for a given
     * model class.
     */
    public static class NoModelLoaderAvailableException extends MissingComponentException {
        public NoModelLoaderAvailableException(Object model) {
            super("Failed to find any ModelLoaders for model: " + model);
        }

        public NoModelLoaderAvailableException(Class<?> modelClass, Class<?> dataClass) {
            super("Failed to find any ModelLoaders for model: " + modelClass + " and data: " + dataClass);
        }
    }

    /**
     * Thrown when no {@link ResourceEncoder} is registered for a given resource class.
     */
    public static class NoResultEncoderAvailableException extends MissingComponentException {
        public NoResultEncoderAvailableException(Class<?> resourceClass) {
            super("Failed to find result encoder for resource class: " + resourceClass);
        }
    }

    /**
     * Thrown when no {@link Encoder} is registered for a given data class.
     */
    public static class NoSourceEncoderAvailableException extends MissingComponentException {
        public NoSourceEncoderAvailableException(Class<?> dataClass) {
            super("Failed to find source encoder for data class: " + dataClass);
        }
    }

    /**
     * Thrown when some necessary component is missing for a load.
     */
    public static class MissingComponentException extends RuntimeException {
        public MissingComponentException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when no {@link ImageHeaderParser} is registered.
     */
    public static final class NoImageHeaderParserException extends MissingComponentException {
        public NoImageHeaderParserException() {
            super("Failed to find image header parser.");
        }
    }
}