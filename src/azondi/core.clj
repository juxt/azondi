(ns azondi.core
  (:require
   jig
   [clojure.java.io :as io]
   [clojure.core.async :refer (alts!!)]
   [clojure.tools.logging :refer :all]
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

(defn tuner-page [req]
  (->
   (stencil/render
            ((::template-loader req) "generic-page.html")
            {:title "Azondi"
             :main "azondi.tuner"})
   ring-resp/response
   (ring-resp/content-type "text/html")
   (ring-resp/charset "utf-8")))

(defn hello [req]
  (ring-resp/response "Hello World!")
  )

(defn wrap-template-loader [template-loader]
  (fn [h]
    (fn [req]
      (h (assoc req ::template-loader template-loader)))))

(deftype WebServices [config]
  Lifecycle
  (init [_ system]
    (let [template-loader
          (get-in system [(:jig/id (satisfying-dependency system config 'jig.stencil/StencilLoader)) :jig.stencil/loader])]

      (-> system
          (add-bidi-routes
           config
           ["/" [["tuner.html" (->WrapMiddleware tuner-page (wrap-template-loader template-loader))]
                 ["hello.html" hello]
                 ["" (->Redirect 307 tuner-page)]
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
