package com.bumptech.glide.load.resource.transcode;

import com.bumptech.glide.load.engine.Resource;

/**
 * Transcodes a resource of one type to a resource of another type.
 * <p>
 * 将一种资源类型转换成另一种资源类型的接口类
 *
 * @param <Z> The type of the resource that will be transcoded from.
 * @param <R> The type of the resource that will be transcoded to.
 */
public interface ResourceTranscoder<Z, R> {

    /**
     * Transcodes the given resource to the new resource type and returns the new resource.
     *
     * @param toTranscode The resource to transcode.
     */
    Resource<R> transcode(Resource<Z> toTranscode);
}