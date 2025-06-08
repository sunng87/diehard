(ns diehard.rate-limiter-test
  (:require [clojure.test :refer [deftest testing is]]
            [diehard.rate-limiter :as rl])
  (:import [java.time Duration]
           [java.util.concurrent ExecutorService Executors Future]))

(def default-error
  "Relative error in percent. 3% by default."
  0.03)

(defn- approx==
  ([expected actual]
   (approx== expected actual (* default-error expected)))
  ([expected actual tolerance]
   (< (abs (- expected actual)) tolerance)))

(defn- submit
  ^Future [^ExecutorService executor ^Callable f]
  (ExecutorService/.submit executor f))

(defn- run-rate-limited-counting
  [rate-limiter run-time-sec]
  (let [threads 32
        pool (Executors/newFixedThreadPool threads)

        counter (atom 0)]
    (doseq [_ (range threads)]
      (submit pool (fn []
                     (while true
                       (rl/acquire! rate-limiter)
                       (swap! counter inc)))))
    (Thread/sleep (Duration/ofSeconds run-time-sec))
    (ExecutorService/.shutdown pool)
    @counter))

(deftest rate-limiter-test
  (testing "high rates"
    (testing "interruptible sleep"
      (let [rate 1000
            rate-limiter (rl/rate-limiter {:rate rate})
            time-sec 2
            count (run-rate-limited-counting rate-limiter time-sec)]
        (is (approx== (* rate time-sec) count))))

    (testing "uninterruptible sleep"
      (let [rate 1000
            rate-limiter (rl/rate-limiter {:rate     rate
                                           :sleep-fn rl/uninterruptible-sleep})
            time-sec 2
            count (run-rate-limited-counting rate-limiter time-sec)]
        (is (approx== (* rate time-sec) count)))))

  (testing "low rates (less than 1.0)"
    (testing "interruptible sleep"
      (let [rate 0.5
            rate-limiter (rl/rate-limiter {:rate rate})
            time-sec 5
            count (run-rate-limited-counting rate-limiter time-sec)]
        ;; exact error tolerance, since we are dealing with much slower ticks
        (is (approx== (* rate time-sec) count 1))))

    (testing "uninterruptible sleep"
      (let [rate 0.5
            rate-limiter (rl/rate-limiter {:rate     rate
                                           :sleep-fn rl/uninterruptible-sleep})
            time-sec 5
            count (run-rate-limited-counting rate-limiter time-sec)]
        ;; exact error tolerance, since we are dealing with much slower ticks
        (is (approx== (* rate time-sec) count 1))))))
