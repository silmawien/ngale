(ns ngale.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.reload :as reload]
            [ring.middleware.json :as json]
            [ring.util.response :as response]
            [cheshire.core :as cheshire]
            [org.httpkit.timer :refer [schedule-task]]
            [org.httpkit.server :refer [with-channel on-close close send!]]
            [ngale.player :as player]
            [ngale.songs :as songs]
            [ngale.util :refer [add-watch-once]]))

(defonce sse-channels (atom {}))

(defn sse-message
  [player]
  (str "data: " (cheshire/generate-string player) "\n\n"))

(defn sse-handler [request]
  "Add sse client and send current state"
  (with-channel request channel
    (swap! sse-channels assoc channel request)
    (on-close channel (fn [status]
                        (swap! sse-channels dissoc channel)
                        (println "channel closed, " status)))
    (send! channel {:status 200 :headers {"Content-Type" "text/event-stream"}
                    :body (sse-message @player)} false)))

(defn on-player-update
  "Broadcast new player state to connected clients"
  [key ref old new]
  (doseq [channel (keys @sse-channels)]
    (send! channel (sse-message new) false)))

(add-watch-once player :handler #'on-player-update)

(defroutes app-routes
  (GET "/" [] (response/resource-response "index.html" {:root "public"}))
  (GET "/playlist" []
       {:body (update-in @player/player [:tracks] songs/add-meta :path)})
  (GET "/q/:query" [query] {:body (songs/query @songs/songs query)})
  (GET "/async" [] sse-handler)
  (GET "/control/pause" [] (player/pause))
  (GET "/control/resume" [] (player/resume))
  (GET "/control/clear" [] (player/clear))
  (GET "/control/next" [] (player/next-track))
  (GET "/control/previous" [] (player/previous-track))
  (GET "/control/enqueue/:path" [path] (player/enqueue path))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site #'app-routes)
      (json/wrap-json-response {:pretty true})))

(def dev-app
  (reload/wrap-reload #'app))

