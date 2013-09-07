(ns ngale.server
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.reload :as reload]
            [ring.middleware.json :as json]
            [ring.util.response :as response]
            [cheshire.core :as cheshire]
            [org.httpkit.server :refer [with-channel on-close close send!]]
            [ngale.app :as app]))

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
                    :body (sse-message @app/playstate)} false)))

(defn on-player-update
  "Broadcast new player state to connected clients"
  [key reference old new]
  (doseq [channel (keys @sse-channels)]
    (send! channel (sse-message new) false)))

(app/add-watch-once :broadcast #'on-player-update)

(defroutes app-routes
  (GET "/" [] (response/resource-response "index.html" {:root "public"}))
  (GET "/playlist" [] {:body (app/playlist)})
  (GET "/q/:q" [q] {:body (app/query q)})
  (GET "/async" [] sse-handler)
  (GET "/pause" [] (app/pause))
  (GET "/resume" [] (app/resume))
  (GET "/clear" [] (app/clear))
  (GET "/next" [] (app/next-track))
  (GET "/previous" [] (app/previous-track))
  (POST "/enqueue" [:as {body :body}]
        (doseq [path (if (coll? body) body [body])]
          (app/enqueue path))
        "")
  (route/resources "/")
  (route/not-found "Not Found"))

(def core-app
  (-> (handler/site #'app-routes)
      (json/wrap-json-body {:keywords? true})
      (json/wrap-json-response {:pretty true})))

(def dev-app
  (reload/wrap-reload #'core-app))

