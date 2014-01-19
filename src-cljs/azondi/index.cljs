(ns azondi.index)

(enable-console-print!)

(println "Hello, welcome to index!")

(let [ws (js/WebSocket. "ws://localhost:8000/events")]
  (set! (.-onmessage ws)
        (fn [ev]
          (let [message (.-data ev)]
            (println "A message arrived! " message)
            ))))
