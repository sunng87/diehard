(ns diehard.ratelimiter)

(defprotocol IRateLimiter
  (acquire! [this] [this permits])
  (try-acquire [this] [this permits] [this permits wait-time]))

(defn- refill [rate-limiter]
  (dosync
   ;; refill
   (let [now (System/currentTimeMillis)]
     (alter (.-reserved-tokens rate-limiter)
            (fn [tokens]
              (max (- (.-max-tokens rate-limiter))
                   (- tokens (* (- now @(.-last-refill-ts rate-limiter))
                                (.-rate rate-limiter))))))
     (ref-set (.-last-refill-ts rate-limiter) now))))

(defn- acquire-sleep-ms [rate-limiter permits]
  (dosync
   (let [pending-tokens (alter (.-reserved-tokens rate-limiter) + permits)]
     (if (<= pending-tokens 0)
       0
       ;; time as milliseconds
       (long (/ pending-tokens (.-rate rate-limiter)))))))

(defn- try-acquire-sleep-ms [rate-limiter permits max-wait-ms]
  (dosync
   (let [current-pending-tokens @(.-reserved-tokens rate-limiter)
         pending-tokens (alter (.-reserved-tokens rate-limiter)
                               (fn [pending-tokens]
                                 ;; test if we can pass in wait period
                                 (if (<= (- (+ pending-tokens permits)
                                            (* max-wait-ms (.-rate rate-limiter)))
                                         0)
                                   (+ pending-tokens permits)
                                   pending-tokens)))]
     (if (= pending-tokens current-pending-tokens)
       false
       (if (<= pending-tokens 0)
         0
         (long (/ pending-tokens (.-rate rate-limiter))))))))

(defn- do-acquire [rate-limiter permits]
  (refill rate-limiter)
  (acquire-sleep-ms rate-limiter permits))

(defn- do-try-acquire [rate-limiter permits max-wait-ms]
  (refill rate-limiter)
  (try-acquire-sleep-ms rate-limiter permits max-wait-ms))

(defrecord TokenBucketRateLimiter [rate max-tokens
                                   ;; internal state
                                   reserved-tokens last-refill-ts]
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
                           (ref (double 0)) (ref (System/currentTimeMillis))))
