(ns azondi.mqtt
  (:require
   jig
   [clojure.tools.logging :refer :all]
   [clojure.core.async :refer (chan >!! close!)]
   [clojurewerkz.machine-head.client :as mh])
  (:import
   (jig Lifecycle)))


(deftype MqttBridge [config]
  Lifecycle
  (init [_ system]
    (let [ch (chan 100)
          ;;db-ch (chan 100)
          ]
      (infof "Assoc'ing in channel into path %s" [(:jig/id config) :channel])
      (-> system
          (assoc-in [(:jig/id config) :channel] ch)
          ;;(assoc-in [(:jig/id config) :db-channel] db-ch)
)))

  (start [_ system]
    (let [id (mh/generate-id)
          client (mh/connect (:uri config) (last (clojure.string/split id #"\.")))
          ch (get-in system [(:jig/id config) :channel])
          ;;db-ch (get-in system [(:jig/id config) :db-channel])
          subscriber (mh/subscribe client (:topics config)
                                   (fn [^String topic meta ^bytes payload]
                                     (debugf "Received message on topic %s: %s" topic (String. payload))
                                     (debugf "Relaying message to core.async channel")
                                     (>!! ch {:topic topic :payload (String. payload)})
                                     ;;(>!! db-ch {:topic topic :payload (String. payload)})
                                     ))]
      (assoc-in system [(:jig/id config) :client] client)))

  (stop [_ system]
    ;; Close the channels we created
    (close! (get-in system [(:jig/id config) :channel]))
    ;;(close! (get-in system [(:jig/id config) :db-channel]))
    ;; Unsubscribe from topics and disconnect from the MQTT broker
    (let [client (get-in system [(:jig/id config) :client])]
      (mh/unsubscribe client (:topics config))
      (mh/disconnect client))
    system))
