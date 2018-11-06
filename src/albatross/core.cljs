(ns ^:figwheel-hooks albatross.core
  (:require
   [clojure.string :as string]
   [albatross.docker :as docker]
   [albatross.kubernetes :as k8s]
#_   [weaver.core :as w]
 #_  ["fs" :as fs]))

(enable-console-print!)

(println "This text is printed from src/albatross/core.cljs. Go ahead and edit it and see reloading in action.")

(defonce child-process (js/require "child_process"))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Hello world!"}))

(defn docker-instructions
  "[albatross-config] -
    Pulls the docker key out of the config and compiles the instruction list for all docker configs. If :docker is a map, it is treated as a one-element list of repos"
  [{docker :docker
    :as root-config}]
  (into
   ["echo beginning docker publish step..."]
   (mapcat
    docker/docker-repo-instructions
    (cond
      (map? docker) [docker]
      (vector? docker) docker
      :else
      (do
        (println docker)
        (throw (js/Error. ":docker value must be either a map or a vector of maps." docker)))))))


(defn k8s-instructions
  "[albatross-config] -
    Pulls the :kubernetes key out of the config and compiles the instruction list for all kubernetes configs. If :kubernetes is a map, it is treated as a one-element list of repos"
  [{k8s :kubernetes
    :as root-config}]
  (into
   ["echo beginning kubernetes step..."]
   (mapcat
    k8s/k8s-cluster-instructions
    (cond
      (map? k8s) [k8s]
      (vector? k8s) k8s
      :else
      (do
        (println k8s)
        ;;TODO Change error message
        (throw (js/Error. ":kubernetes value must be either a map or a vector of maps." k8s)))))))


;;TODO: add git and leiningen steps to albatross
;;TODO: compose all top level instruction sets

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  (.log js/console "Reloaded!"))
