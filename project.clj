(defproject ngale "0.1.0-SNAPSHOT"
  :description "Simple remote control for alsaplayer"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler ngale.handler/app}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]
                        [ring-serve "0.1.2"]]}})
