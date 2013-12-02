(ns azondi.core
  (:require
   jig
   [clojure.java.io :as io]
   [clojure.core.async :refer (alts!!)]
   [clojure.tools.logging :refer :all]
   [jig.web.app :refer (add-routes)]
   [jig.web.stencil :refer (link-to-stencil-loader get-template)]
   [io.pedestal.service.interceptor :as interceptor :refer (defbefore definterceptorfn interceptor)]
   [io.pedestal.service.http.sse :as sse]
   [ring.util.response :refer (redirect) :as ring-resp]
   [ring.util.codec :as codec]
   [cheshire.core :as json]
   [stencil.core :as stencil])
  (:import
   (jig Lifecycle)
   (java.util.concurrent Executors TimeUnit)))


(defbefore radio-page [{:keys [system component] :as context}]
  (assoc context
    :response
    (ring-resp/response
     (stencil/render
      (get-template system component "radio.html")
      {}
      ))))

(defbefore tuner-page [{:keys [system component] :as context}]
  (assoc context
    :response
    (ring-resp/response
     (stencil/render
      (get-template system component "generic-page.html")
      {:main "opensensors.tuner"}
      ))))

(defn get-template-with-check [& args]
  {:post [%]}
  (apply get-template args))




(defbefore root-page
  [{:keys [url-for] :as context}]
  (assoc context :response
         (ring-resp/response "hi")))

(definterceptorfn static
  [name root-path & [opts]]
  (interceptor/handler
   name
   (fn [req]
     (ring-resp/file-response
      (codec/url-decode (get-in req [:path-params :static]))
      {:root root-path, :index-files? true, :allow-symlinks? false}))))

(defn bridge-events-to-sse-subscribers
  "Continuously take events from the channel and send the event to the
subscribers. The subscribers are supplied in an atom and those which
return a java.io.IOException are removed."
  [channels subscribers]
  (loop []
    (when-let [[msg _] (alts!! channels)]
      (debugf "Delivering message to %d SSE subscribers: %s"
              (count @subscribers) (:payload msg))
      (swap! subscribers
             (fn [subs]
               (debugf "Sending message to SSE subscribers: %s" msg)
               (doall ;; don't be lazy, otherwise events can be sent through in batches
                (keep ;; those subscribers which don't barf
                 #(try
                    (sse/send-event % "message" (json/generate-string msg))
                    % ;; return the subscriber to keep it
                    (catch java.io.IOException ioe
                      ;; return nil to remove it
                      ))
                 subs))))
      (recur))))

(defn get-channel-from-system [system path]
  (let [ch (get-in system path)]
    (assert ch (format "Expecting to see input channel registered in system at path %s, but found nothing (or nil), keys to system are %s" path (keys system)))
    ch))

(deftype ServerSentEventBridge [config]
  Lifecycle
  (init [_ system]
    (let [channel-paths (:inputs config)
          channels (map (partial get-channel-from-system system) channel-paths)
          subscribers (atom [])
          sse-bridge-thread (when (pos? (count channels))
                              (Thread. ^Runnable #(bridge-events-to-sse-subscribers channels subscribers)))]
      (-> system
          ;; Put messages on this channel to send to SSE subscribers
          (assoc ::sse-subscribers subscribers)
          ;; For starting and stopping the service
          (assoc ::sse-bridge-thread sse-bridge-thread))))

  (start [_ {t ::sse-bridge-thread :as system}]
    (when t
      (.start t))
    system)

  (stop [_ {t ::sse-bridge-thread subscribers ::sse-subscribers :as system}]
    (when t
      (.stop t))
    (doseq [sub @subscribers]
      (sse/end-event-stream sub))
    system))

(deftype WebServices [config]
  Lifecycle
  (init [_ system]
    (let [subscribers (::sse-subscribers system)]
      (assert subscribers
              (format "Subscribers atom must be registered in system under %s prior to the init of %s"
                      ::subscribers (:jig/id config)))
      (-> system
          (add-routes
           config
           [["/" {:get root-page}]
            ["/radio.html" {:get radio-page}]
            ["/tuner.html" {:get tuner-page}]
            ["/*static" {:get (static ::static (:static-path config))}]
            ["/events" {:get [::events (sse/start-event-stream (partial swap! subscribers conj))]}]
            ])
          (link-to-stencil-loader config))))

  (start [_ system] system)
  (stop [_ system] system))

;; A system-wide thread pool for scheduled threads
(deftype ScheduledThreadPool [config]
  Lifecycle

  (init [_ system]
    ;; TODO 10 should come from config
    (assoc system ::scheduled-thread-pool (Executors/newScheduledThreadPool 10)))

  (start [_ system] system)

  (stop [_ {tpool ::scheduled-thread-pool :as system}]
    (debugf "Stopping scheduled thread pool")
    (.awaitTermination tpool 1 TimeUnit/SECONDS)
    (when-not (.isShutdown tpool)
      (debugf "Shutdown now of scheduled thread pool")
      (.shutdownNow tpool))
    (debugf "Stopped scheduled thread pool")
    system))
