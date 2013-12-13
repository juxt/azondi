(ns azondi.dummy
  (:require
   jig
   [clojure.core.async :refer (chan >!! timeout <!! go-loop)]
   [clojure.tools.logging :refer :all])
  (:import
   (jig Lifecycle)
   (java.util.concurrent TimeUnit)))

(deftype RandomQuoteGenerator [config]
  Lifecycle

  (init [_ system]
    (let [ch (chan 100)]
      (infof "Assoc'ing in channel into path %s" [(:jig/id config) :channel])
      (-> system
          (assoc-in [(:jig/id config) :channel] ch))))

  (start [_ {tpool :azondi.core/scheduled-thread-pool :as system}]
    (let [ch (get-in system [(:jig/id config) :channel])]
      (assert tpool (format "No thread pool registered in system (should be key %s)" :azondi.core/scheduled-thread-pool))
      (assoc system
        ::event-producer
        (.scheduleAtFixedRate tpool
                              #(let [quote (rand-nth (:quotes config))]
                                 (debugf "Emiting quote: %s" quote)
                                 (>!! ch {:topic "/test/quotes" :payload quote}))
                              0 (:delay-in-ms config) TimeUnit/MILLISECONDS))))

  (stop [_ {p ::event-producer :as system}]
    (debugf "Stopping event generator")
    ;; Cancel the job
    (debugf "Cancelling job")
    (.cancel p true)
    (debugf "Stopped event generator")
    system))


(deftype ErraticPulse [config]
  Lifecycle
  (init [_ system]
    (let [ch (chan 10)]
      (-> system (assoc-in [(:jig/id config) :channel] ch))))

  (start [_ system]
    (let [ch (get-in system [(:jig/id config) :channel])]
      (go-loop []
               (<!! (timeout (rand-int 1000)))
               (>!! ch {:topic "/test/erratic-pulse" :payload "pulse"})
               (recur))
      system))

  (stop [_ system]
    system))
