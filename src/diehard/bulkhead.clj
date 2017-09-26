(ns diehard.bulkhead
  (:import [java.util.concurrent Semaphore TimeUnit]))

(defprotocol IBulkhead
  (acquire! [this] [this timeout-ms])
  (release! [this]))

(defrecord Bulkhead [semaphore]
  IBulkhead
  (acquire! [this] (.acquire ^Semaphore semaphore))
  (acquire! [this timeout-ms]
    (when-not (.tryAcquire semaphore timeout-ms TimeUnit/MILLISECONDS)
      (throw (ex-info "Failed to acquire bulkhead" {::bulkhead :failed
                                                    ::timeout timeout-ms}))))
  (release! [this]
    (.release ^Semaphore semaphore)))

(defn bulkhead [concurrency]
  (Bulkhead. concurrency))

(defmacro with-bulkhead [bulkhead-opts & body]
  `(let [{bulkhead :bulkhead} bulkhead-opts]
     (acquire! bulkhead)
     (try
       ~@body
       (finally
         (release! bulkhead)))))
