package com.bumptech.glide.manager;

import com.bumptech.glide.request.Request;
import com.bumptech.glide.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A class for tracking, canceling, and restarting in progress, completed, and failed requests.
 * <p>
 * 管理{@link Request}的开始和暂停，并支持多个{@link Request}的存放和延迟处理
 * <p>
 * This class is not thread safe and must be accessed on the main thread.
 * <p>
 * 该类并非线程安全，必须在主线程中访问
 */
public class RequestTracker {
    // Most requests will be for views and will therefore be held strongly (and safely) by the view
    // via the tag. However, a user can always pass in a different type of target which may end up not
    // being strongly referenced even though the user still would like the request to finish. Weak
    // references are therefore only really functional in this context for view targets. Despite the
    // side affects, WeakReferences are still essentially required. A user can always make repeated
    // requests into targets other than views, or use an activity manager in a fragment pager where
    // holding strong references would steadily leak bitmaps and/or views.
    private final Set<Request> requests =
            Collections.newSetFromMap(new WeakHashMap<Request, Boolean>());
    // A set of requests that have not completed and are queued to be run again. We use this list to
    // maintain hard references to these requests to ensure that they are not garbage collected
    // before
    // they start running or while they are paused. See #346.
    /**
     * 用来存放延迟请求的集合（比如当前应用处于暂停状态，那么就将请求添加到这个集合中，当应用再次唤醒，
     * 那么再执行这里面存放的延迟请求）
     */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<Request> pendingRequests = new ArrayList<>();
    private boolean isPaused;

    /**
     * Starts tracking the given request.
     */
    public void runRequest(Request request) {
        requests.add(request);
        if (!isPaused) {
            request.begin();
        } else {
            pendingRequests.add(request);
        }
    }

    // Visible for testing.
    void addRequest(Request request) {
        requests.add(request);
    }

    /**
     * Stops tracking the given request, clears, and recycles it, and returns {@code true} if the
     * request was removed or {@code false} if the request was not found.
     * <p>
     * 清除请求并将该请求从存放所有请求的集合中移除掉
     */
    public boolean clearRemoveAndRecycle(Request request) {
        boolean isOwnedByUs =
                request != null && (requests.remove(request) || pendingRequests.remove(request));
        if (isOwnedByUs) {
            request.clear();
            request.recycle();
        }
        return isOwnedByUs;
    }

    /**
     * Returns {@code true} if requests are currently paused, and {@code false} otherwise.
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * Stops any in progress requests.
     * <p>
     * 暂停进程中的所有{@link Request}
     */
    public void pauseRequests() {
        isPaused = true;
        for (Request request : Util.getSnapshot(requests)) {
            if (request.isRunning()) {
                request.pause();
                pendingRequests.add(request);
            }
        }
    }

    /**
     * Starts any not yet completed or failed requests.
     */
    public void resumeRequests() {
        isPaused = false;
        for (Request request : Util.getSnapshot(requests)) {
            if (!request.isComplete() && !request.isCancelled() && !request.isRunning()) {
                request.begin();
            }
        }
        pendingRequests.clear();
    }

    /**
     * Cancels all requests and clears their resources.
     * <p>
     * After this call requests cannot be restarted.
     */
    public void clearRequests() {
        for (Request request : Util.getSnapshot(requests)) {
            clearRemoveAndRecycle(request);
        }
        pendingRequests.clear();
    }

    /**
     * Restarts failed requests and cancels and restarts in progress requests.
     */
    public void restartRequests() {
        for (Request request : Util.getSnapshot(requests)) {
            if (!request.isComplete() && !request.isCancelled()) {
                // Ensure the request will be restarted in onResume.
                request.pause();
                if (!isPaused) {
                    request.begin();
                } else {
                    pendingRequests.add(request);
                }
            }
        }
    }

    @Override
    public String toString() {
        return super.toString() + "{numRequests=" + requests.size() + ", isPaused=" + isPaused + "}";
    }
}
