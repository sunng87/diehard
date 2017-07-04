(ns diehard.rate-limiter)

(defprotocol IRateLimiter
  (acquire! [this] [this permits])
  (try-acquire [this] [this permits] [this permits wait-time]))

(defn- refill [rate-limiter]
  ;; refill
  (let [now (System/currentTimeMillis)]
    (swap! (.-state rate-limiter)
           (fn [state]
             (-> state
                 (update :reserved-tokens
                         #(max (- (.-max-tokens rate-limiter))
                               (- % (* (- now (:last-refill-ts state))
                                       (.-rate rate-limiter)))))
                 (assoc :last-refill-ts now))))))

(defn- acquire-sleep-ms [rate-limiter permits]
  (let [{pending-tokens :reserved-tokens} (swap! (.-state rate-limiter)
                                                 update :reserved-tokens + permits)]
    (if (<= pending-tokens 0)
      0
      ;; time as milliseconds
      (long (/ pending-tokens (.-rate rate-limiter))))))

(defn- try-acquire-sleep-ms [rate-limiter permits max-wait-ms]
  (try
    (let [{pending-tokens :reserved-tokens}
          (swap! (.-state rate-limiter)
                 (fn [state]
                   (update state :reserved-tokens
                           (fn [pending-tokens]
                             ;; test if we can pass in wait period
                             (if (<= (- (+ pending-tokens permits)
                                        (* max-wait-ms (.-rate rate-limiter)))
                                     0)
                               (+ pending-tokens permits)
                               (throw (ex-info "Not enough quota." {})))))))]
      (if (<= pending-tokens 0)
        0
        (long (/ pending-tokens (.-rate rate-limiter)))))
    (catch clojure.lang.ExceptionInfo _
      false)))

(defn- do-acquire [rate-limiter permits]
  (refill rate-limiter)
  (acquire-sleep-ms rate-limiter permits))

(defn- do-try-acquire [rate-limiter permits max-wait-ms]
  (refill rate-limiter)
  (try-acquire-sleep-ms rate-limiter permits max-wait-ms))

(defrecord TokenBucketRateLimiter [rate max-tokens
                                   ;; internal state
                                   state]
  IRateLimiter
  (acquire! [this]
    (acquire! this 1))
  (acquire! [this permits]
    (let [sleep (do-acquire this permits)]
      (when (> sleep 0)
        (Thread/sleep sleep))))
  (try-acquire [this]
    (try-acquire this 1))
  (try-acquire [this permits]
    (try-acquire this permits 0))
  (try-acquire [this permits wait-ms]
    (let [sleep (do-try-acquire this permits wait-ms)]
      (if (false? sleep)
        false
        (do
          (when (> sleep 0)
            (Thread/sleep sleep))
          true)))))

(defn rate-limiter [rate max-tokens]
  (TokenBucketRateLimiter. (/ (double rate) 1000) max-tokens
                           (atom {:reserved-tokens (double 0)
                                  :last-refill-ts (System/currentTimeMillis)})))
