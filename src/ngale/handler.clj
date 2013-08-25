(ns ngale.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.reload :as reload]
            [ring.middleware.json :as json]
            [org.httpkit.timer :refer [schedule-task]]
            [org.httpkit.server :refer [with-channel on-close close send!]]
            [ngale.player :refer [player]]
            [ngale.songs :as songs]))

(defn async-handler [request]
  (with-channel request channel
    (on-close channel (fn [status] (println "channel closed, " status)))
    (loop [id 0]
      (when (< id 5)
        (schedule-task (* id 500)
                       (send! channel (str "msg from server #" id "\n") false))
        (recur (inc id))))
    (schedule-task 3000 (close channel))))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/playlist" [] (pr-str @player))
  (GET "/q/:query" [query] {:body (songs/query @songs/songs query)})
  (GET "/async" [] async-handler)
  (GET "/foo" [] "bar")
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site #'app-routes)
      (json/wrap-json-response {:pretty true})))

(def dev-app
  (reload/wrap-reload #'app))

