(ns azondi.tuner
  (:require-macros
   [cljs.core.async.macros :refer [go]]
   [dommy.macros :refer [node sel1]]
   )

  (:require
   [goog.dom :as dom]
   [goog.events :as events]
   [goog.json :refer (parse)]

   [domina :refer (append! log-debug set-html! set-text!)]
   [domina.events :refer (listen!)]
   [domina.css :refer (sel)]
   [domina.css :as ds]
   [domina.xpath :as dx]

   [dommy.utils :as utils]
   [dommy.core :as dommy]

   [azondi.dataflow :as dataflow]
   [cljs.core.async :refer [<! put! chan mult tap]])

  (:import [goog.net Jsonp]
           [goog Uri]))

(def stations ["radio1" "radio2" "radio3" "radio4" "radio4extra" "5live" "radioscotland" "quotes"])

(let [el (sel1 "#stations")]
  (doseq [s stations]
    (dommy/append! el [:tr [:td s] [:td {:id (str "bbc/livetext/" s)}]])))

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
  (let [flow (dataflow/create-dataflow)
        messages-el (sel1 "#messages")
        ]
    ;; We're not using this dataflow yet, but we will soon (perhaps) The
    ;; idea is that this might be a complex UI component that needs
    ;; incremental drawing, for example, a chart. But right now it's
    ;; just a list of messages.
    (go
     ;; The process keeps its state in the loop bindings
     (loop [log (list)]
       (let [evt (<! ch)
             log (take 10 (conj log [(:topic evt) (:payload evt)]))]
         (dommy/replace-contents!
          messages-el
          (for [[topic payload] log]
            [:tr [:td topic][:td payload]]))
         (recur log))))))

(defn trigger-animation [ch]
  (let [anim (sel1 "#animMessageArrival")
        txt (sel1 "#mqttMessageText")]
    (go
     (loop []
       (let [evt (<! ch)]
         (when txt
           (set! (.-innerHTML txt) (:payload evt))))
       (when anim
         (.beginElement anim))
       (recur)))))

(def default-duration "0.25s")

(defn trigger-subscribing-animation
  [ch]
  (let [path-id "device-mqtt-subscribe-path"
        mpath (.createElement js/document "mpath")
        parent (sel1 "#device-mqtt-subscribe")

        path (sel1 "#device-mqtt-subscribe path")
        ;; Graphviz doesn't put an id on this path, so let's do that first.
        _ (.setAttribute path "id" path-id)

        ;; We have to create the animateMotion element manually via JS
        ;; interop because dommy doesn't include this in its set of SVG tags (yet)
        anim (.createElementNS js/document "http://www.w3.org/2000/svg" "animateMotion")
        _ (.setAttribute anim "begin" "indefinite")
        _ (.setAttribute anim "dur" default-duration)

        ;; We have to create the mpath child by hand so that we can reference the path-id.
        mpath (.createElementNS js/document "http://www.w3.org/2000/svg" "mpath")
        _ (.setAttributeNS mpath "http://www.w3.org/1999/xlink" "href" (str "#" path-id))
        _ (.appendChild anim mpath)

        g (node [:g
                 [:circle {:cx 0 :cy 0 :r 8 :fill "red" :stroke "black"}]
                 anim])]

    (dommy/hide! g)
    (listen! anim "beginEvent" (fn [_] (dommy/show! g)))
    (listen! anim "endEvent" (fn [_] (dommy/hide! g)))
    (dommy/append! parent g)

    ;; Add an info panel
    (let [message-el (node [:text {:font-family "monospace" :font-size 14 :x 120 :y 60} "(message)"])
          topic-el (node [:text {:font-family "monospace" :font-size 14 :x 120 :y 80} "(topic)"])]
      (dommy/append!
       parent
       (node [:g
              [:text {:font-family "serif" :font-size 14 :x 0 :y 60} "Last message:"] message-el
              [:text {:font-family "serif" :font-size 14 :x 0 :y 80} "Topic:"] topic-el
              ]))

      (go
       (loop []
         (let [{:keys [topic payload]} (<! ch)]
           (dommy/set-text! topic-el topic)
           (dommy/set-text! message-el payload)
           (.beginElement anim)

           )
         (recur))))))

(defn trigger-publishing-animation
  []
  (let [path-id "device-mqtt-publish-path"
        mpath (.createElement js/document "mpath")
        parent (sel1 "#device-mqtt-publish")

        path (sel1 "#device-mqtt-publish path")
        ;; Graphviz doesn't put an id on this path, so let's do that first.
        _ (.setAttribute path "id" path-id)

        ;; We have to create the animateMotion element manually via JS
        ;; interop because dommy doesn't include this in its set of SVG tags (yet)
        anim (.createElementNS js/document "http://www.w3.org/2000/svg" "animateMotion")
        _ (.setAttribute anim "begin" "indefinite")
        _ (.setAttribute anim "dur" default-duration)

        ;; We have to create the mpath child by hand so that we can reference the path-id.
        mpath (.createElementNS js/document "http://www.w3.org/2000/svg" "mpath")
        _ (.setAttributeNS mpath "http://www.w3.org/1999/xlink" "href" (str "#" path-id))
        _ (.appendChild anim mpath)

        g (node [:g
                 [:circle {:cx 0 :cy 0 :r 8 :fill "red" :stroke "black"}]
                 anim])]

    (dommy/hide! g)
    (listen! anim "beginEvent" (fn [_] (dommy/show! g)))
    (listen! anim "endEvent" (fn [_] (dommy/hide! g)))
    (dommy/append! parent g)

    (dommy/listen! (sel1 "#my-device") :click (fn [evt] (.beginElement anim)))))

(defn init
  "Start all the individual processes"
  []
  (let [mlt (mult (get-events))] ; multiplex the Server Sent Events, so each process can
    (message-counter (tap mlt (chan)) (sel "#message-count"))
    (radio-table (tap mlt (chan)))
    (message-log (tap mlt (chan)))

    ;; Slides are piggybacking on this right now
    (trigger-animation (tap mlt (chan)))
    (trigger-subscribing-animation (tap mlt (chan)))
    (trigger-publishing-animation)))

(init)
