(ns diehard.bulkhead
  (:import [java.util.concurrent Semaphore TimeUnit]))

(def ^{:const true :no-doc true}
  allowed-bulkhead-option-keys
  #{:concurrency})

(defprotocol IBulkhead
  (acquire! [this] [this timeout-ms])
  (release! [this]))

(defrecord Bulkhead [semaphore]
  IBulkhead
  (acquire! [this] (.acquire ^Semaphore semaphore))
  (acquire! [this timeout-ms]
    (when-not (.tryAcquire ^Semaphore semaphore timeout-ms TimeUnit/MILLISECONDS)
      (throw (ex-info "Failed to acquire semaphore" {:bulkhead true
                                                     :max-wait-ms timeout-ms}))))
  (release! [this]
    (.release ^Semaphore semaphore)))

(defn bulkhead
  "Create bulkhead with given configuration:
  * `concurrency`: the max number of concurrent executions"
  [opts]
  (if-let [concurrency (:concurrency opts)]
    (Bulkhead. (Semaphore. concurrency))
    (throw (IllegalArgumentException. ":concurrency is required for bulkhead"))))
