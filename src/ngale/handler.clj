(ns ngale.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [ring.middleware.reload :as reload]
            [compojure.route :as route]))

(defroutes app-routes
  (GET "/" [] "Hello World 3")
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))

(def dev-app
  (reload/wrap-reload #'app '(ngale/handler)))
