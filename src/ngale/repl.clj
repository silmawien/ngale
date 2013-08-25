(ns ngale.repl
  (:require [org.httpkit.server :refer [run-server]])
  (:require [ngale.handler :as handler]))

(defn reload
  []
  (println "Reloading ngale namespaces ...")
  (require '[ngale.songs :as songs] :reload)
  (require '[ngale.player :as player] :reload)
  (require '[ngale.handler :as handler] :reload))

(defonce server nil)

(defn unserve
  []
  (when server
    (println "stopping ...")
    (alter-var-root #'server (fn [v] (v) nil))))

(defn serve
  []
  (unserve)
  (println "starting ...")
  (alter-var-root #'server (fn [v] (run-server handler/dev-app {:port 3000}))))

