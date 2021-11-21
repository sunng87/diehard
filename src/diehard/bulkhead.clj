(ns diehard.bulkhead
  (:import [java.util.concurrent Semaphore TimeUnit]))

(def ^{:const true :no-doc true}
  allowed-bulkhead-option-keys
  #{:concurrency})

(defprotocol IBulkhead
  (acquire! [_] [this timeout-ms])
  (release! [_]))

(defrecord Bulkhead [semaphore]
  IBulkhead
  (acquire! [_] (.acquire ^Semaphore semaphore))
  (acquire! [_ timeout-ms]
    (when-not (.tryAcquire ^Semaphore semaphore timeout-ms TimeUnit/MILLISECONDS)
      (throw (ex-info "Failed to acquire semaphore" {:bulkhead true
                                                     :max-wait-ms timeout-ms}))))
  (release! [_]
    (.release ^Semaphore semaphore)))

(defn bulkhead
  "Create bulkhead with given configuration:
  * `concurrency`: the max number of concurrent executions"
  [opts]
  (if-let [concurrency (:concurrency opts)]
    (Bulkhead. (Semaphore. concurrency))
    (throw (IllegalArgumentException. ":concurrency is required for bulkhead"))))
