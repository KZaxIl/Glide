package com.bumptech.glide.load.engine;

import android.graphics.drawable.Drawable;

import com.bumptech.glide.GlideContext;
import com.bumptech.glide.Priority;
import com.bumptech.glide.Registry;
import com.bumptech.glide.load.Encoder;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceEncoder;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.cache.DiskCache;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoader.LoadData;
import com.bumptech.glide.load.resource.UnitTransformation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解码相关参数的获取
 */
final class DecodeHelper<Transcode> {

    private final List<LoadData<?>> loadData = new ArrayList<>();
    private final List<Key> cacheKeys = new ArrayList<>();

    private GlideContext glideContext;
    /**
     * 一般的，等价于{@link com.bumptech.glide.RequestBuilder#load(Object)}中的参数
     */
    private Object model;
    private int width;
    private int height;
    /**
     * @see {@link com.bumptech.glide.request.BaseRequestOptions#resourceClass}
     * <p>
     * 调用{@link com.bumptech.glide.request.RequestOptions#decodeTypeOf(Class)}来改变
     */
    private Class<?> resourceClass;
    private DecodeJob.DiskCacheProvider diskCacheProvider;
    private Options options;
    /**
     * @see {@link com.bumptech.glide.request.BaseRequestOptions#transformations}
     */
    private Map<Class<?>, Transformation<?>> transformations;
    /**
     * @see com.bumptech.glide.RequestManager#as(Class)
     * <p>
     * 默认若调用{@link com.bumptech.glide.RequestManager#load(Object)}，则为{@link Drawable}类
     */
    private Class<Transcode> transcodeClass;
    /**
     * {@code true}表示{@link #getLoadData()}是第一次被调用
     */
    private boolean isLoadDataSet;
    /**
     * {@code true}表示{@link #getCacheKeys()}是第一次被调用
     */
    private boolean isCacheKeysSet;
    private Key signature;
    private Priority priority;
    private DiskCacheStrategy diskCacheStrategy;
    private boolean isTransformationRequired;

    @SuppressWarnings("unchecked")
    <R> DecodeHelper<R> init(
            GlideContext glideContext,
            Object model,
            Key signature,
            int width,
            int height,
            DiskCacheStrategy diskCacheStrategy,
            Class<?> resourceClass,
            Class<R> transcodeClass,
            Priority priority,
            Options options,
            Map<Class<?>, Transformation<?>> transformations,
            boolean isTransformationRequired,
            DecodeJob.DiskCacheProvider diskCacheProvider) {
        this.glideContext = glideContext;
        this.model = model;
        this.signature = signature;
        this.width = width;
        this.height = height;
        this.diskCacheStrategy = diskCacheStrategy;
        this.resourceClass = resourceClass;
        this.diskCacheProvider = diskCacheProvider;
        this.transcodeClass = (Class<Transcode>) transcodeClass;
        this.priority = priority;
        this.options = options;
        this.transformations = transformations;
        this.isTransformationRequired = isTransformationRequired;

        return (DecodeHelper<R>) this;
    }

    Object getModel() {
        return model;
    }

    void clear() {
        glideContext = null;
        model = null;
        signature = null;
        resourceClass = null;
        transcodeClass = null;
        options = null;
        priority = null;
        transformations = null;
        diskCacheStrategy = null;

        loadData.clear();
        isLoadDataSet = false;
        cacheKeys.clear();
        isCacheKeysSet = false;
    }

    DiskCache getDiskCache() {
        return diskCacheProvider.getDiskCache();
    }

    DiskCacheStrategy getDiskCacheStrategy() {
        return diskCacheStrategy;
    }

    Priority getPriority() {
        return priority;
    }

    Options getOptions() {
        return options;
    }

    Key getSignature() {
        return signature;
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    List<Class<?>> getRegisteredResourceClasses() {
        return glideContext.getRegistry()
                .getRegisteredResourceClasses(model.getClass(), resourceClass, transcodeClass);
    }

    boolean hasLoadPath(Class<?> dataClass) {
        return getLoadPath(dataClass) != null;
    }

    <Data> LoadPath<Data, ?, Transcode> getLoadPath(Class<Data> dataClass) {
        return glideContext.getRegistry().getLoadPath(dataClass, resourceClass, transcodeClass);
    }

    @SuppressWarnings("unchecked")
    <Z> Transformation<Z> getTransformation(Class<Z> resourceClass) {
        Transformation<Z> result = (Transformation<Z>) transformations.get(resourceClass);
        if (result == null) {
            if (transformations.isEmpty() && isTransformationRequired) {
                throw new IllegalArgumentException(
                        "Missing transformation for " + resourceClass + ". If you wish to"
                                + " ignore unknown resource types, use the optional transformation methods.");
            } else {
                return UnitTransformation.get();
            }
        }
        return result;
    }

    boolean isResourceEncoderAvailable(Resource<?> resource) {
        return glideContext.getRegistry().isResourceEncoderAvailable(resource);
    }

    <Z> ResourceEncoder<Z> getResultEncoder(Resource<Z> resource) {
        return glideContext.getRegistry().getResultEncoder(resource);
    }

    List<ModelLoader<File, ?>> getModelLoaders(File file)
            throws Registry.NoModelLoaderAvailableException {
        return glideContext.getRegistry().getModelLoaders(file);
    }

    boolean isSourceKey(Key key) {
        List<LoadData<?>> loadData = getLoadData();
        int size = loadData.size();
        for (int i = 0; i < size; i++) {
            LoadData<?> current = loadData.get(i);
            if (current.sourceKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    List<LoadData<?>> getLoadData() {
        if (!isLoadDataSet) {
            isLoadDataSet = true;
            loadData.clear();
            List<ModelLoader<Object, ?>> modelLoaders = glideContext.getRegistry().getModelLoaders(model);
            int size = modelLoaders.size();
            for (int i = 0; i < size; i++) {
                ModelLoader<Object, ?> modelLoader = modelLoaders.get(i);
                LoadData<?> current =
                        modelLoader.buildLoadData(model, width, height, options);
                if (current != null) {
                    loadData.add(current);
                }
            }
        }
        return loadData;
    }

    List<Key> getCacheKeys() {
        if (!isCacheKeysSet) {
            isCacheKeysSet = true;
            cacheKeys.clear();
            List<LoadData<?>> loadData = getLoadData();
            int size = loadData.size();
            for (int i = 0; i < size; i++) {
                LoadData<?> data = loadData.get(i);
                if (!cacheKeys.contains(data.sourceKey)) {
                    cacheKeys.add(data.sourceKey);
                }
                for (int j = 0; j < data.alternateKeys.size(); j++) {
                    if (!cacheKeys.contains(data.alternateKeys.get(j))) {
                        cacheKeys.add(data.alternateKeys.get(j));
                    }
                }
            }
        }
        return cacheKeys;
    }

    <X> Encoder<X> getSourceEncoder(X data) throws Registry.NoSourceEncoderAvailableException {
        return glideContext.getRegistry().getSourceEncoder(data);
    }
}
