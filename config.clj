{:jig/components
 ;; Keys are usually keywords or strings, but can be anything (uuids, urls, ...)
 {
  :server
  {:jig/component jig.web.server/Component
   :io.pedestal.service.http/port 8000
   :io.pedestal.service.http/type :jetty
   }

  :opensensors/web
  {:jig/component jig.web.app/Component
   :jig/dependencies [:server]
   :jig/scheme :http
   :jig/hostname "localhost"
   :jig.web/server :server
   }

  :opensensors/mosquitto-bridge
  {:jig/component opensensors.mqtt/MqttBridge
   :jig/project "../azondi/project.clj"
   :uri "tcp://test.mosquitto.org:1883"
   :topics ["bbc/livetext/#"
            "energy/generation/realtime/intned/#"]
   }

  :opensensors/opensensors-bridge
  {:jig/component opensensors.mqtt/MqttBridge
   :jig/project "../mqtt.opensensors.io/project.clj"
;; :uri "tcp://adl-rabbitmq.juxt.pro:1883"
   :uri "tcp://mqtt.opensensors.io:1883"
   :topics ["#"]
   }

  :opensensors/sse-bridge
  {:jig/component opensensors.core/ServerSentEventBridge
   :jig/project "../mqtt.opensensors.io/project.clj"
   :jig/dependencies [:opensensors/opensensors-bridge :opensensors/mosquitto-bridge :opensensors/dummy-event-generator]
   :inputs [[:opensensors/opensensors-bridge :channel]
            [:opensensors/mosquitto-bridge :channel]
            [:opensensors/dummy-event-generator :channel]
            ]}

:opensensors/stencil-loader
  {:jig/component jig.web.stencil/StencilLoader}

  :opensensors/service
  {:jig/component opensensors.core/WebServices
   :jig/project "../azondi/project.clj"
   :jig/dependencies [:opensensors/web :opensensors/sse-bridge :opensensors/stencil-loader :cljs-server]
   :jig/stencil-loader :opensensors/stencil-loader
   :jig.web/app-name :opensensors/web
   :static-path "../azondi/public"
   }

:cljs-builder
  {:jig/component jig.cljs/Builder
   :jig/project "../azondi/project.clj"
   :output-dir "../azondi/target/js"
   :output-to "../azondi/target/js/main.js"
   :source-map "../azondi/target/js/main.js.map"
   :optimizations :none
;; :pretty-print true
;; :clean-build true
   }


   :opensensors/dummy-event-generator
 {:jig/component opensensors.dummy/DummyEventGenerator
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
   }}}
