# azondi

Uses the [Communicating Sequential Processes](http://en.wikipedia.org/wiki/Communicating_sequential_processes) also known as asyncronous programming to process real time sensor data in clojure for [opensensors.io](http://opensensors.io).  We rely on clojure's core.async library heavily.

azondi takes a continous stream of messages from the test Mosquitto MQTT broker and creating an [MQTT bridge](https://github.com/OpenSensorsIO/azondi/blob/master/src/azondi/mqtt.clj). Using Server Side Events the data is pushed to the [browser](https://github.com/OpenSensorsIO/azondi/blob/master/src/azondi/core.clj)

Clojurescript is then used to build the dataflow to process the data in the browser.

## Incubated with Jig

Writing 'quick and dirty' Clojure back-ends is fun, simple and easy. When Clojure systems get larger, separation of concerns becomes a harder problem. We know we should do it (that's progress at least) but it's easier [said](http://www.infoq.com/presentations/Simple-Made-Easy) than done.

Therefore, this project is incubated with [Jig](https://github.com/juxt/jig). Jig pushes a separation of concerns into configurable components, while retaining the rapid development environment Clojure developers are used to. Jig is designed to be optional at deployment time.

Further documentation on the azondi development workflow using JIG can be found [here](JIG.md)

## Usage

This is still an early version not quite read for production use.

We are using azondi to process sensor data on extremely large volumes and in a distributed manner.  It is still in development but we welcome any feedback.

## License

Copyright © 2013 opensensors.io

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

The use and distribution terms for this software are covered by the [Eclipse Public License 1.0](http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound by the terms of this license. You must not remove this notice, or any other, from this software.
