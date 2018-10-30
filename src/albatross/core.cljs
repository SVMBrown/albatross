(ns ^:figwheel-hooks albatross.core
  (:require
   [clojure.string :as string]
#_   [weaver.core :as w]
 #_  ["fs" :as fs]))

(enable-console-print!)

(println "This text is printed from src/albatross/core.cljs. Go ahead and edit it and see reloading in action.")

(defonce child-process (js/require "child_process"))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Hello world!"}))

(defn docker-image-instructions
  "[albatross-config repo-config image-config] -
   Compiles the instruction list for an image"
  [_
   {url :url}
   {image-name :name
    tags :tags
    workspace :workspace
    :as image}]
  (into
   [(str "echo 'handling docker image: " image-name "'")
    (str "docker build -t " url image-name " " (string/join " " (map
                                                                 #(str "-t " url image-name ":" %)
                                                                 tags)) " " workspace)
    (str "docker push " url image-name)]
   (map #(str "docker push " url image-name ":" %) tags)))

(defn docker-repo-instructions
  "[albatross-config repo-config] -
   Compiles the instruction list for a repo config"
  [albatross-config
   {url :url
    user :user
    pass :pass
    images :images
    :as repo-config}]
  (when-not (string? url)
    (throw (js/Error. ":docker step must have a valid (string) url")))
  (into
   [(str "echo 'handling docker publishing step for repository: " url "'")
    (str "docker login -u " user " -p " pass " " url)]
   (mapcat
    (partial docker-image-instructions
             albatross-config
             repo-config)
    images)))

(defn docker-instructions
  "[albatross-config] -
    Pulls the docker key out of the config and compiles the instruction list for all docker configs. If :docker is a map, it is treated as a one-element list of repos"
  [{docker :docker
    :as root-config}]
  (into
   ["echo beginning docker publish step..."]
   (mapcat
    (partial docker-repo-instructions
             (dissoc root-config :docker))
    (cond
      (map? docker) [docker]
      (vector? docker) docker
      :else (do
              (println docker)
              (throw (js/Error. ":docker value must be either a map or a vector of maps." docker)))))))



;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  (.log js/console "Reloaded!"))
