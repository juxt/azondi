(ns azondi.tuner
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [dommy.macros :refer [node sel1 by-id]]
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
   [dommy.core :as d]

   [cljs.core.async :refer [<! put! chan mult tap timeout sliding-buffer filter<]])

  (:import [goog.net Jsonp]
           [goog Uri]))

(def stations ["radio1" "radio2" "radio3" "radio4" "radio4extra" "5live" "radioscotland" "quotes"])

(let [el (sel1 "#stations")]
  (doseq [s stations]
    (d/append! el [:tr [:td s] [:td {:id (str "bbc/livetext/" s)}]])))

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
  (let [messages-el (sel1 "#messages")]
    (go
     ;; The process keeps its state in the loop bindings
     (loop [log (list)]
       (let [evt (<! ch)
             log (take 10 (conj log [(:topic evt) (:payload evt)]))]
         (d/replace-contents!
          messages-el
          (for [[topic payload] log]
            [:tr [:td topic][:td payload]]))
         (recur log))))))

#_(defn trigger-animation [ch]
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

#_(defn trigger-subscribing-animation
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

    (d/hide! g)
    (listen! anim "beginEvent" (fn [_] (d/show! g)))
    (listen! anim "endEvent" (fn [_] (d/hide! g)))
    (d/append! parent g)

    ;; Add an info panel
    (let [message-el (node [:text {:font-family "monospace" :font-size 14 :x 120 :y 60} "(message)"])
          topic-el (node [:text {:font-family "monospace" :font-size 14 :x 120 :y 80} "(topic)"])]
      (d/append!
       parent
       (node [:g
              [:text {:font-family "serif" :font-size 14 :x 0 :y 60} "Last message:"] message-el
              [:text {:font-family "serif" :font-size 14 :x 0 :y 80} "Topic:"] topic-el
              ]))

      (go
       (loop []
         (let [{:keys [topic payload]} (<! ch)]
           (d/set-text! topic-el topic)
           (d/set-text! message-el payload)
           (.beginElement anim)

           )
         (recur))))))

#_(defn trigger-publishing-animation
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

    (d/hide! g)
    (listen! anim "beginEvent" (fn [_] (d/show! g)))
    (listen! anim "endEvent" (fn [_] (d/hide! g)))
    (d/append! parent g)

    (d/listen! (sel1 "#my-device") :click (fn [evt] (.beginElement anim)))))

(def rand-ints (vec (take 50 (map inc (repeatedly #(rand-int 9))))))

(defn tune [topic ch]
  (filter< (comp (partial = topic) :topic) ch))

(defn now []
  (.now js/Date))

(defn make-ring-buffer [size]
  (let [result (make-array 2)]
    (aset result 0 0)
    (aset result 1 (make-array size))
    result))

;; state: [t cnt]
;; ring buf: [pos nums]
(defn counter [topic ch ringbuf]
  (let [ch (tune topic ch)
        state (make-array 2)
        ringbuf (make-ring-buffer 10)
        ]
    (aset state 0 (now))
    (aset state 1 0)
    (go
     (loop []
       (let [[msg c] (alts! [ch (timeout 100)])]
         (if (< (now) (+ (aget state 0) 2000))
           (condp = c
             ;; Increment count
             ch (aset state 1 (inc (aget state 1)))
             nil)
           (do
             (.log js/console "Count in last 2000" (aget state 1))
             ;; Store result in ring buffer
             (aset (aget ringbuf 1) (aget ringbuf 0) (aget state 1))
             (aset ringbuf 0 (let [pos (aget ringbuf 0)]
                               (.log js/console "pos is" pos)
                               (.log js/console "ringbuf length is " (.-length (aget ringbuf 1)))
                               (if (>= pos (dec (.-length (aget ringbuf 1)))) 0 (inc pos))))
             (.log js/console "Ringbuf" ringbuf)

             ;; Advance time
             (aset state 0 (+ (aget state 0) 2000))
             ;; Reset count
             (aset state 1 (condp = c ch 1 0)))))
       (recur)))))

(defn meter [topic ch rb]
  (let [meter-width 180
        meter (node [:rect {:x 0 :y 0 :width meter-width :height 20 :stroke "white" :fill "green"}])
        rate (node [:text {:x 80 :y 10} "-1"])
        ch (tune topic ch)]

    (d/prepend! (sel1 :#content)
                (node [:svg {:width 120 :height 30 :style "border: 1px dotted black"}
                       [:g {:transform "translate(2,2) scale(0.2)"}
                        [:rect {:x -2 :y -2 :width (+ meter-width 4) :height 24 :stroke "black" :fill "none"}]
                        meter
                        (for [x (range 10 meter-width 10)]
                          [:rect {:x (dec x) :y 0 :width 2 :height 20 :stroke "none" :fill "white"}])]
                       rate]

                      ))
    (d/prepend! (sel1 :#content) (node [:p "Meter: " topic]))

    (let [decay 20]
      (go
       (loop [w meter-width]
         (let [[msg c] (alts! [ch (timeout 50)])
               nw (if (= c ch) meter-width (- w decay))]
           (d/set-attr! meter :width nw)
           (recur nw)))))

    (go-loop []
             (<! (timeout 2000))
             (.log js/console "ringbuf is" rb)
             (.log js/console "Setting rate to " (str (aget (aget rb 1) (dec (aget rb 0)))))
             (d/set-text! rate (str (aget (aget rb 1) (aget rb 0))))
             (recur)
             )

    ))

(defn init
  "Start all the individual processes"
  []
  (let [mlt (mult (get-events))] ; multiplex the Server Sent Events, so each process can
    #_(message-counter (tap mlt (chan)) (sel "#message-count"))
    #_(radio-table (tap mlt (chan)))
    #_(message-log (tap mlt (chan)))

    (let [rb (make-ring-buffer 10)]
      (counter "/test/erratic-pulse" (tap mlt (chan 10)) rb)

      #_(meter "/test/quotes" (tap mlt (chan (sliding-buffer 1))) (make-ring-buffer 10))
      (meter "/test/erratic-pulse" (tap mlt (chan (sliding-buffer 1))) rb))


    ;; Slides are piggybacking on this right now
    #_(trigger-animation (tap mlt (chan)))
    #_(trigger-subscribing-animation (tap mlt (chan)))
    #_(trigger-publishing-animation)))

(let [ws (js/WebSocket. "ws://localhost:8000/events")]
  (set! (.-onmessage ws)
        (fn [ev]
          (let [message (.-data ev)]
            (.log js/console "A message!" message)
            ))))

(set! (.-onload js/window)
      (fn []
        (let [a (make-array 10)]
          (aset a 0 "a")
          (.log js/console a)
          )
        (init)))
