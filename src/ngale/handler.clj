(ns ngale.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [ring.middleware.reload :as reload]
            [ring.middleware.json :as json]
            [ring.util.response :refer [response]]
            [compojure.route :as route]
            [ngale.player :refer [player]]
            [ngale.songs :as songs]))

(defroutes app-routes
  (GET "/" [] "Hello World 3")
  (GET "/playlist" [] (pr-str @player))
  (GET "/q/:query" [query] {:body (songs/query @songs/songs query)})
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site app-routes)
      (json/wrap-json-response {:pretty true})))

(def dev-app
  (reload/wrap-reload #'app '(ngale/handler)))
