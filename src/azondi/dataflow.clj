(ns azondi.dataflow
  (:require
   [clojure.pprint :refer (pprint)]
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

(let [dataflow
      (dataflow/build
       (-> {:version 2
            :transform [[:event [:**] store-event]]
            :emit [[#{[:counter][:counter2]} (app/default-emitter [:main])]]
            :derive [
                     [#{[:*]} [:counter] inc-counter :vals]

                     ]}
           (app/adapt-description)
           (#'app/standardize-pre-if-exists)
           (#'app/standardize-post-app-model-if-exists)
           (update-in [:emit] #'app/ensure-default-emitter)
           (update-in [:input-adapter] #'app/ensure-input-adapter)
           (update-in [:transform] #'app/rekey-transforms)))]

  (->
   (reduce (fn [state message]
             (dataflow/run state dataflow message))
           {:data-model {}}
           [{msg/type :event msg/topic [:a] :payload "A"}
            {msg/type :event msg/topic [:bbc :livetext :radio5] :payload "News"}
            {msg/type :event msg/topic [:bbc :livetext :radio4] :payload "The Archers"}
            ])
   :data-model
   )
)
