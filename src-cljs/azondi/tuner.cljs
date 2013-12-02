(ns azondi.tuner
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [domina :refer (append! log-debug set-html! set-text!)]
            [domina.events :refer (listen!)]
            [goog.json :refer (parse)]
            [domina.css :refer (sel)]
            [domina.css :as ds]
            [domina.xpath :as dx]
            [azondi.dataflow :as dataflow]
            [cljs.core.async :refer [<! put! chan mult tap]])
  (:import [goog.net Jsonp]
           [goog Uri]))

(def stations ["radio1" "radio2" "radio3" "radio4" "radio4extra" "5live" "radioscotland" "quotes"])

(let [el (sel "#stations")]
  (doseq [s stations]
    (append! el (str "<tr><td>" s "</td><td id=\"" "bbc/livetext/" s "\"></td></tr>"))))

(defn event->clj [evt]
  (-> evt .-evt .-event_ .-data parse (js->clj :keywordize-keys true)))

(defn get-events
  "Retrieve message as server-sent events"
  []
  (let [ch (chan 100)]
    (listen! (new js/EventSource "/events")
             :message
             (fn [evt]
               (go
                (put! ch (event->clj evt)))))
    ch))

(defn message-counter
  "A simple CSP process which counts messages and sets the text to an element."
  [ch el]
  (go
   (loop [cnt 0]
     (<! ch)
     (set-text! el (str (inc cnt)))
     (recur (inc cnt)))))

(defn radio-table
  "A CSP process which matches messages to radio stations, setting the
table cell to the latest payload for that radio station."
  [ch]
  (go
   (loop []
     (let [evt (<! ch)]
       (when-let [td (sel (str "#" (:topic evt)))]
         (set-html! td (:payload evt)))
       (recur)))))

(defn message-log
  "A CSP process which adds messages to a table"
  [ch]
  (let [flow (dataflow/create-dataflow)]
    ;; We're not using this dataflow yet, but we will soon (perhaps) The
    ;; idea is that this might be a complex UI component that needs
    ;; incremental drawing, for example, a chart. But right now it's
    ;; just a list of messages.
    (go
     (loop []
       (let [evt (<! ch)]
         (append! (sel "#messages") (str "<tr><td>" (:topic evt) "</td><td>" (:payload evt) "</td></tr>"))
         (recur))))))

(defn init
  "Start all the individual processes"
  []
  (let [mlt (mult (get-events))] ; multiplex the Server Sent Events, so each process can
    (message-counter (tap mlt (chan)) (sel "#message-count"))
    (radio-table (tap mlt (chan)))
    (message-log (tap mlt (chan)))))

(init)
