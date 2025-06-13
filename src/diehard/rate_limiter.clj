(ns diehard.rate-limiter)

(defprotocol IRateLimiter
  (acquire!
    [this]
    [this permits]
    "Acquire given number of permits. It will block until there are permits available.")
  (try-acquire
    [this]
    [this permits]
    [this permits wait-time]
    "Try to acquire given number of permits, allows blocking for at most `wait-ms` milliseconds.
    Return true if there are enough permits in permitted time."))

(declare refill do-acquire! do-try-acquire)

(defrecord TokenBucketRateLimiter [rate max-tokens
                                   ;; internal state
                                   state sleep-fn]
  IRateLimiter
  (acquire! [this]
    (acquire! this 1))
  (acquire! [this permits]
    (refill this)
    (do-acquire! this permits))
  (try-acquire [this]
    (try-acquire this 1))
  (try-acquire [this permits]
    (try-acquire this permits 0))
  (try-acquire [this permits wait-ms]
    (refill this)
    (do-try-acquire this permits wait-ms)))

(defn- refill [^TokenBucketRateLimiter rate-limiter]
  (let [now (System/currentTimeMillis)]
    (swap! (.-state rate-limiter)
           (fn [state]
             (-> state
                 (update :reserved-tokens
                         #(if (> (:last-refill-ts state) 0)
                            (max (- (.-max-tokens rate-limiter))
                                 (- % (* (- now (:last-refill-ts state))
                                         (.-rate rate-limiter))))
                            %))
                 (assoc :last-refill-ts now))))))

(defn- ->sleep-ms ^long [pending-tokens rate]
  (if (<= pending-tokens 0) 0 (long (/ pending-tokens rate))))

(defn- do-acquire!
  [^TokenBucketRateLimiter rate-limiter permits]
  (let [state (swap! (.-state rate-limiter) update :reserved-tokens + permits)
        sleep-ms (->sleep-ms (:reserved-tokens state) (.-rate rate-limiter))]
    ((.-sleep-fn rate-limiter) sleep-ms)))

(defn- do-try-acquire
  [^TokenBucketRateLimiter rate-limiter permits max-wait-ms]
  (try
    (let [state (swap! (.-state rate-limiter)
                       (fn [state]
                         (update state :reserved-tokens
                                 (fn [pending-tokens]
                                   ;; test if we can pass in wait period
                                   (if (<= (- (+ pending-tokens permits)
                                              (* max-wait-ms (.-rate rate-limiter)))
                                           0)
                                     (+ pending-tokens permits)
                                     (throw (ex-info "Not enough permits"
                                                     {:rate-limiter true})))))))
          sleep-ms (->sleep-ms (:reserved-tokens state) (.-rate rate-limiter))]
      ((.-sleep-fn rate-limiter) sleep-ms)
      true)
    (catch Exception e
      (if-not (:rate-limiter (ex-data e))
        (throw e)
        false))))

(defn interruptible-sleep [^long ms]
  (when (pos? ms)
    (Thread/sleep ms)))

(def ^:private nanos-in-ms 1000000)

(defn uninterruptible-sleep [^long ms]
  (when (pos? ms)
    (let [end-time-ns (+ (System/nanoTime) (* nanos-in-ms ms))]
      (with-local-vars [interrupted? false]
        (try (loop []
               (let [remaining-ns (- end-time-ns (System/nanoTime))]
                 ;; required sleep duration fully consumed — exit
                 (when (< 0 remaining-ns)
                   (try
                     (Thread/sleep (quot remaining-ns nanos-in-ms)
                                   (mod remaining-ns nanos-in-ms))
                     ;; successful sleep — exit
                     (catch InterruptedException _
                       (var-set interrupted? true)))
                   ;; can only recur from tail position
                   (when @interrupted? (recur)))))
             (finally
               (when @interrupted?
                 (Thread/.interrupt (Thread/currentThread)))))))))

(defn rate-limiter
  "Create a default rate limiter with:
  * `rate`: permits per second (may be a floating point, e.g. 0.5 <=> 1 req every 2 sec)
  * `max-cached-tokens`: the max size of tokens that the bucket can cache when it's idle
  * `sleep-fn`: a unary fn of millis to sleep for, allowing for custom sleep semantics;
                by default, sleeps interruptedly; pass `uninterruptible-sleep` to sleep
                uninterruptedly"
  [{:keys [rate max-cached-tokens sleep-fn] :as _opts}]
  (if (some? rate)
    (let [max-cached-tokens (or max-cached-tokens (int rate))
          sleep-fn (or sleep-fn interruptible-sleep)]
      (TokenBucketRateLimiter. (/ (double rate) 1000)
                               max-cached-tokens
                               (atom {:reserved-tokens (double 0)
                                      :last-refill-ts  (long -1)})
                               sleep-fn))
    (throw (IllegalArgumentException. ":rate is required for rate-limiter"))))
