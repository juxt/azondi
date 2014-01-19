(ns azondi.core
  (:require
   jig
   [clojure.core.async :refer (go <!)]
   [clojure.java.io :as io]
   [clojure.core.async :refer (alts!!)]
   [clojure.tools.logging :refer :all]
   [org.httpkit.server :refer (with-channel websocket? on-receive send! on-close)]
   [jig.bidi :refer (add-bidi-routes)]
   [bidi.bidi :refer (->WrapMiddleware ->Redirect ->Resources)]
   [jig.util :refer (satisfying-dependency)]
   [ring.util.response :refer (redirect) :as ring-resp]
   [ring.util.codec :as codec]
   [cheshire.core :as json]
   [stencil.core :as stencil])
  (:import
   (jig Lifecycle)
   (java.util.concurrent Executors TimeUnit)))

(defn template-page [req template-name cljs-main]
  (->
   (stencil/render
            ((::template-loader req) template-name)
            {:title "Azondi"
             :main cljs-main})
   ring-resp/response
   (ring-resp/content-type "text/html")
   (ring-resp/charset "utf-8")))

(defn tuner-page [req]
  (template-page req "generic-page.html" "azondi.tuner"))

(defn index-page [req]
  (template-page req "generic-page.html" "azondi.index"))

(defn wrap-template-loader [template-loader]
  (fn [h]
    (fn [req]
      (println "Incoming request")
      (h (assoc req ::template-loader template-loader)))))

(deftype WebServices [config]
  Lifecycle
  (init [_ system]
    (let [template-loader
          (get-in system [(:jig/id (satisfying-dependency system config 'jig.stencil/StencilLoader)) :jig.stencil/loader])]

      (-> system
          (add-bidi-routes
           config
           ["/" [["" (->WrapMiddleware [["index.html" index-page]
                                        ["tuner.html" tuner-page]]
                                       (wrap-template-loader template-loader))]
                 ["tuner.html" (->WrapMiddleware tuner-page
                                       (wrap-template-loader template-loader))]
                 ["" (->Redirect 307 index-page)]
                 ["" (->Resources {:prefix "public/"})]
                 ]]))))
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

;; Web sockets

(defn create-receive-handler [ch]
  (fn [data]
    (println "Data received on channel:" data)))

(def websocket-connections (atom []))

(deftype MqttWebSocketBridge [config]
  Lifecycle
  (init [_ system] system)
  (start [_ system]
    (let [notifications (some (comp :channel system) (:jig/dependencies config))]
      (when notifications
        (go (loop []
              (let [msg (<! notifications)]
                (when msg
                  (doseq [c @websocket-connections]
                    (send! c (pr-str msg)))))
              (recur)
              )))
      (-> system
          (add-bidi-routes
           config
           ["/events"
            (fn [req]
              (with-channel req channel
                (when (websocket? channel)
                  (swap! websocket-connections conj channel)
                  (send! channel "Hello!")
                  (on-receive channel (create-receive-handler channel))
                  (on-close channel (fn [status] (swap! websocket-connections #(remove #{channel}  %)))))))]))))
  (stop [_ system] system))
