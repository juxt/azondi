(ns azondi.dataflow
  (:require
   [clojure.set :as set]
   [io.pedestal.app :as app]
   [io.pedestal.app.dataflow :as dataflow]
   [io.pedestal.app.protocols :as p]
   [io.pedestal.app.messages :as msg]))

;; OK, let's build a dataflow

(defn inc-transform [old-value message]
  ((fnil inc 0) old-value)
)

(defn add-transform [old-value {:keys [payload]}]
  (reverse (take 3 (cons payload (reverse old-value)))))

(defn handle-event [old-value {:keys [payload]}]
  (reverse (take 5 (cons payload (reverse old-value))))
  )

(defn store-event [_ event]
  event)

;; So we can just strip away Pedestal plumbing and use the dataflow directly. Great!

(defn inc-counter [a b]
  (+ (or a 0) 1)
  )

;; These are private in dataflow.clj, and this is clojurescript so we can't apply the vars
(defn standardize-pre-if-exists [description]
  (if (:pre description)
    (update-in description [:pre] dataflow/transform-maps)
    description))

(defn standardize-post-app-model-if-exists [description]
  (if (-> description :post :app-model)
    (update-in description [:post :app-model] dataflow/transform-maps)
    description))

(defn- ensure-input-adapter [input-adapter]
  (if-not input-adapter
    (fn [m] {:key (msg/type m) :out (msg/topic m)})
    input-adapter))

(defn rekey-transforms [transforms]
  (mapv #(if (map? %)
           (set/rename-keys % {msg/type :key msg/topic :out})
           %)
        transforms))

(defn create-dataflow []
  (dataflow/build
   (-> {:version 2
        :transform [[:event [:**] store-event]]
        :emit [[#{[:counter][:counter2]} (app/default-emitter [:main])]]
        :derive [[#{[:*]} [:counter] inc-counter :vals]]}
       (app/adapt-description)
       (standardize-pre-if-exists)
       (standardize-post-app-model-if-exists)
       (update-in [:input-adapter] ensure-input-adapter)
       (update-in [:transform] rekey-transforms))))
