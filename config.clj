;; This declarative config tells Jig which components to run
{:jig/components
 ;; Keys are usually keywords or strings, but can be anything (uuids, urls, ...)
 {
  :opensensors/stencil-loader
  {
   :jig/component jig.stencil/StencilLoader
   :jig/project "../azondi/project.clj"}


  :opensensors/mosquitto-bridge
  {:jig/component azondi.mqtt-bridge/MqttBridge
   :jig/project "../azondi/project.clj"
   :uri "tcp://test.mosquitto.org:1883"
   :topics ["bbc/livetext/#"
            "energy/generation/realtime/intned/#"]
   }

  #_:opensensors/opensensors-bridge
  #_{:jig/component azondi.mqtt-bridge/MqttBridge
     :jig/project "../azondi/project.clj"
     :uri "tcp://mqtt.opensensors.io:1883"
     :topics ["#"]
     }

  :opensensors/scheduled-thread-pool
  {:jig/component azondi.core/ScheduledThreadPool
   :jig/project "../azondi/project.clj"
   }

  :opensensors/dummy-event-generator
  {:jig/component azondi.dummy/RandomQuoteGenerator
   :jig/project "../azondi/project.clj"
   :jig/dependencies [:opensensors/scheduled-thread-pool]
   :delay-in-ms 1000
   :quotes
   ["The best way to predict the future is to invent it. -Alan Kay"
    "A point of view is worth 80 IQ points. -Alan Kay"
    "Lisp isn't a language, it's a building material. -Alan Kay"
    "Simple things should be simple, complex things should be possible. -Alan Kay"
    "Measuring programming progress by lines of code is like measuring aircraft building progress by weight. -Bill Gates"
    "Controlling complexity is the essence of computer programming. -Brian Kernighan"
    "The unavoidable price of reliability is simplicity. -C.A.R. Hoare"
    "You're bound to be unhappy if you optimize everything. -Donald Knuth"
    "Simplicity is prerequisite for reliability. -Edsger W. Dijkstra"
    "Deleted code is debugged code. -Jeff Sickel"
    "The key to performance is elegance, not battalions of special cases. -Jon Bentley and Doug McIlroy"
    "First, solve the problem. Then, write the code. -John Johnson"
    "Simplicity is the ultimate sophistication. -Leonardo da Vinci"
    "Programming is not about typing... it's about thinking. -Rich Hickey"
    "Design is about pulling things apart. -Rich Hickey"
    "Programmers know the benefits of everything and the tradeoffs of nothing. -Rich Hickey"
    "Code never lies, comments sometimes do. -Ron Jeffries"
    "Take this nREPL, brother, and may it serve you well."
    "Let the hacking commence!"
    "Hacks and glory await!"
    "Hack and be merry!"
    "Your hacking starts... NOW!"
    "May the Source be with you!"
    "May the Source shine upon thy nREPL!"]
   }

  :opensensors/erratic-pulse
  {:jig/component azondi.dummy/ErraticPulse
   :jig/project "../azondi/project.clj"}

  #_:opensensors/sse-bridge
  #_{:jig/component azondi.core/ServerSentEventBridge
     :jig/project "../azondi/project.clj"
     :jig/dependencies [:opensensors/opensensors-bridge :opensensors/mosquitto-bridge :opensensors/dummy-event-generator :opensensors/erratic-pulse]
     :inputs [[:opensensors/opensensors-bridge :channel]
              [:opensensors/mosquitto-bridge :channel]
              [:opensensors/dummy-event-generator :channel]
              [:opensensors/erratic-pulse :channel]
              ]}


  ;; MQTT broker

  :mqtt-decoder
  {:jig/component jig.netty.mqtt/MqttDecoder
   :jig/project "../azondi/project.clj"}

  :mqtt-encoder
  {:jig/component jig.netty.mqtt/MqttEncoder
   :jig/project "../azondi/project.clj"}

  :mqtt-notification-channel
  {:jig/component jig.async/Channel
   :jig/project "../azondi/project.clj"
   :buffer :dropping
   :size 100}

  :mqtt-handler
  {:jig/component azondi.mqtt-broker/NettyMqttHandler
   :jig/project "../azondi/project.clj"
   :jig/dependencies [:mqtt-notification-channel]
   }

  :mqtt-server
  {:jig/component jig.netty/Server
   :jig/dependencies [:mqtt-decoder :mqtt-encoder :mqtt-handler]
   :jig/project "../azondi/project.clj"
   :port 1883}

  :mqtt-web-socket-bridge
  {:jig/component azondi.core/MqttWebSocketBridge
   :jig/project "../azondi/project.clj"
   :jig/dependencies [:mqtt-notification-channel]
   }


  ;; ClojureScript

  :cljs-builder
  {:jig/component jig.cljs-builder/Builder
   :jig/project "../azondi/project.clj"
   :output-dir "../azondi/target/js"
   :output-to "../azondi/target/js/main.js"
   :source-map "../azondi/target/js/main.js.map"
   :optimizations :none
   }

  :cljs-server
  {:jig/component jig.bidi/ClojureScriptRouter
   :jig/dependencies [:cljs-builder]
   :jig.web/context "/js/"
   }

  ;; Web services

  :opensensors/service
  {:jig/component azondi.core/WebServices
   :jig/project "../azondi/project.clj"
   :jig/dependencies [#_:opensensors/sse-bridge :opensensors/stencil-loader]
   :jig/stencil-loader :opensensors/stencil-loader
   :deck-js-path "../deck.js"
   }

  :opensensors/routing
  {:jig/component jig.bidi/Router
   :jig/project "../azondi/project.clj"
   :jig/dependencies [:cljs-server :mqtt-web-socket-bridge :opensensors/service]
   }

  :opensensors/server
  {:jig/component jig.http-kit/Server
   :jig/project "../azondi/project.clj"
   :jig/dependencies [:opensensors/routing]
   :port 8000
   }

  }}
