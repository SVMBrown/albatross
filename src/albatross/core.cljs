(ns ^:figwheel-hooks albatross.core
  (:require
   [clojure.string :as string]
   [albatross.docker :as docker]
   [albatross.kubernetes :as k8s]
   [albatross.weaver :as w]
#_   [weaver.core :as w]
 #_  ["fs" :as fs]))

(enable-console-print!)

(println "This text is printed from src/albatross/core.cljs. Go ahead and edit it and see reloading in action.")

(defonce child-process (js/require "child_process"))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Hello world!"}))

(defn docker-instructions
  "[docker] -
    compiles the instruction list for a docker config (or a list of docker configs)."
  [docker]
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
  "[k8s] -
    Compiles the instruction list for a kubernetes config."
  [k8s]
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

(defmulti deployment-instructions :type)

(defmethod deployment-instructions :default [step]
  (throw (js/Error. (str "Unrecognized deployment step type for deployment step: " step))))

(defmethod deployment-instructions :shell [{:keys [exec]
                                            :as step}]
  (cond
    (string? exec) [exec]
    (and (vector? exec) (every? string? exec)) exec
    :else (throw (js/Error. (str "Deployment step of type :shell must have an :exec key, and :exec must be either a string or a vector of strings.\n" "Step:\n" step)))))

(defmethod deployment-instructions :script [{:keys [file]
                                             :as step}]
  (cond
    (string? file) [(if (some
                         #(string/starts-with? file %)
                         ["/"
                          "./"
                          "../"])
                      file
                      (str "./" file))]
    :else (throw (js/Error. (str "Deployment step of type :script must have a :file key, which is a string.\n" "Step:\n" step)))))



;; EXAMPLE CONFIG
#_ {:some "config"
    :retries "might be specified here"
    :rollback-strategy "maybe?"
    :and-any "other top-level stuff"
    :pre-deploy-hooks [{:type :weaver
                        :some "weaver"
                        :config "options"
                        :that ["will" "be" "transformed" "into" "an" "effectful" "function"]
                        :which ["will" "be" "run" "before" "evaluation" "of" "deploy" "instructions"]
                        :BUT ["will" "be" "run" "AFTER" "the" "generation" "of" "said" "instructions"]}
                       {:type :any-other-effectful-things-similar-to-the-above}
                       {:type :for-example
                        :it-could-be {:git "pull"
                                      :lein "build"
                                      :npm "install"
                                      :or ["any" "other" "effectful" "thing" "you" "may" "need"]}}
                       {:type :shell
                        :command "should have an escape hatch here probably!"}]
    :deployment [{:type :docker
                  :some "docker"
                  :repository "configuration"
                  :images [{:some "single image config"}
                           {:another "image for the same repo"}]}
                 {:type :kubernetes
                  :some "kubernetes"
                  :cluster "configuration"
                  :actions [{:action-type :apply-dir
                             :dir "./k8s" ;; NOTE: if you want to template this directory, it must be done in the pre-deploy-step.
                                          ;;       However, the albatross config is assumed to be weaver templated,
                                          ;;       so you can use weaver to share between k8s action config and weaver config.
                             :action-name "some semantic name for a kubernetes action"}
                            {:action-type :some-other-k8s-action
                             :some-config "for this action"}]}]
    :post-deploy-hooks [{:type :git-or-something
                         :maybe "you"
                         :want "to push a tag"
                         :and/or "send a slack message"}
                        {:type :some-other-effectful-thing
                         :these "will happen in order"
                         :and "only on successful deployment"}
                        {:type :shell
                         :command "should have an escape hatch here too!"}]
    :deploy-failure-hooks [{:type :slack-or-email-or-something?
                            :these "effects"
                            :will "be triggered"
                            :when "a deploy fails"}
                           {:type :shell
                            :command "should have an escape hatch here as well!"}]}
;;NOTE: A lot of the above is not supported yet.
;; It is maybe over the top, but it might be worth considering for future
;; This should maybe not fully replace jenkins, so the scope of above might need to be limited





;;TODO: add git and leiningen steps to albatross
;;TODO: compose all top level instruction sets

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  (.log js/console "Reloaded!"))
