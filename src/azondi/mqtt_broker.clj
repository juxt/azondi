;; Copyright © 2014, OpenSensors.IO. All Rights Reserved.
(ns azondi.mqtt-broker
  (:require
   jig
   [jig.util :refer (satisfying-dependency)]
   [clojure.core.async :refer (put!)])
  (:import
   (io.netty.channel ChannelHandlerAdapter)
   (jig Lifecycle)))

(def registered-topics
  {"/uk/gov/hackney/parking" #{"yodit" "paula"}
   "/juxt/big-red-button/malcolm" #{"malcolm"}
   "/juxt/big-red-button/yodit" #{"yodit"}})

(def valid-users #{["yodit" "letmein"]
                   ["michael" "clojurewerkz!"]
                   ["malcolm" "password"]
                   ["paula" "123"]})

(defn authenticated? [{:keys [username password]}]
  (nil? (true? (valid-users [username password]))))

(defn make-channel-handler [{:keys [subs connections channel]}]
  (proxy [ChannelHandlerAdapter] []
    (channelRead [ctx msg]
      (case (:type msg)

        :connect
        (do
          (println "Got connection!" (pr-str ctx) (pr-str msg))
          ;; TODO Validate connection protocol name and assert version is 3.1
          (dosync (alter connections assoc ctx (assoc msg :authenticated? (authenticated? msg))))
          (.writeAndFlush ctx {:type :connack}))

        :subscribe
        (do (.writeAndFlush ctx {:type :suback})
            (dosync
             (alter subs (fn [subs]
                           (reduce #(update-in %1 [%2] conj ctx)
                                   subs (map first (:topics msg)))))))
        :publish
        (do
          (println "Got message!" (pr-str ctx) (pr-str msg))
          (let [conn (get @connections ctx)]
            (when conn
              (when channel
                (println "putting on core.async channel")
                (put! channel msg))
              (println "Sending response to conn: " conn)
              ;; Handle subscribers, although this should probably happen somewhere else now
              (doseq [ctx (get @subs (:topic msg))]
                (.writeAndFlush ctx msg)))))

        :pingreq (.writeAndFlush ctx {:type :pingresp})

        :disconnect (.close ctx)))
    (exceptionCaught [ctx cause]
      (try (throw cause)
           (finally (.close ctx))))))

(deftype NettyMqttHandler [config]
  Lifecycle
  (init [_ system] system)
  (start [_ system]
    (let [ch (some (comp :channel system) (:jig/dependencies config))]
      (assoc-in system
                [(:jig/id config) :jig.netty/handler-factory]
                #(make-channel-handler {:subs (ref {})
                                        :connections (ref {})
                                        :channel ch}))))
  (stop [_ system] system))
