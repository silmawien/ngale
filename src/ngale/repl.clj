(ns ngale.repl
  (:require [org.httpkit.server :refer [run-server]]
            [ngale.server :as server]))

(defn reload
  []
  (println "Reloading ngale namespaces ...")
  (require '[ngale.songs :as songs] :reload)
  (require '[ngale.player :as player] :reload)
  (require '[ngale.util :as util] :reload)
  (require '[ngale.app :as app] :reload)
  (use '[ngale.cmdline] :reload)
  (require '[ngale.server :as server] :reload))

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
  (alter-var-root #'server (fn [v] (run-server server/dev-app {:port 3000}))))

