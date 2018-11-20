(ns ^:figwheel-hooks albatross.core
  (:require
   [clojure.string :as string]
   [albatross.docker :as docker]
   [albatross.kubernetes :as k8s]
   [albatross.weaver :as w]
   [weaver.core]
 #_  ["fs" :as fs]))

(enable-console-print!)

(println "This text is printed from src/albatross/core.cljs. Go ahead and edit it and see reloading in action.")

(defonce child-process (js/require "child_process"))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Hello world!"}))

(def deployment-step-type :albatross/type)


(def deployment-step-name :albatross/name)

(defmulti deployment-instructions #'deployment-step-type)

(defmethod deployment-instructions :default [step]
  (throw (js/Error. (str "Unrecognized deployment step type: " (deployment-step-type step) " for deployment step: " step))))

;; Escape hatch (string literals)
;; e.g.
;; {:exec "echo $foo"}
;; {:exec ["echo 'Echoing Foo..." "echo $foo"]}
(defmethod deployment-instructions :shell [{:keys [exec]
                                            :as step}]
  (cond
    (string? exec) [exec]
    (and (vector? exec) (every? string? exec)) exec
    :else (throw (js/Error. (str "Deployment step of type :shell must have an :exec key, and :exec must be either a string or a vector of strings.\n" "Step:\n" step)))))

;; Docker (see albatross.docker for details)
(defmethod deployment-instructions :docker [step]
  (docker/docker-repo-instructions step))

;; Kubernetes (see albatross.kubernetes for details)
(defmethod deployment-instructions :kubernetes [step]
  (k8s/k8s-cluster-instructions step))

(def hook-type :albatross/type)

(defmulti generate-hook #'hook-type)

(defmethod generate-hook :default [hook]
  (throw (js/Error. (str "Unrecognized hook type: " (hook-type hook) " for deployment hook: " hook))))

(defmethod generate-hook :weaver [hook]
  (w/weaver-hook hook))

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

(defn generate-pre-deploy-hooks [config]
  (vec
   (map-indexed
    (fn [idx hook]
      (assoc (generate-hook hook)
             :albatross/stage :pre-deploy
             :albatross/hook-number (inc idx)))
    (:pre-deploy-hooks config))))

(defn generate-instructions [config]
  (conj
   (apply
    (comp vec concat)
    (map-indexed
     (fn [idx deployment]
       (let [step-explanation (str  "#"
                                    (inc idx)
                                    (when-some [t (deployment-step-type deployment)]
                                      (str " of type: " t))
                                    (when-some [step-name (deployment-step-name deployment)]
                                      (str " named: " step-name)))]
         ((comp vec concat)
          [(str "echo 'Beginning deployment step "
                step-explanation
                "...'")]
          (deployment-instructions deployment)
          [(str "echo 'Completed deployment step "
                step-explanation
                "!'")])))
     (:deployment config)))
   "echo 'Deployment successfully completed!'"))

(defn exec
  ([command] (exec
              command
              {:timeout 180000
               :encoding "UTF-8"}))
  ([command opts]
   (.execSync child-process
              command
              (->> opts
                   (merge
                    {:timeout 180000
                     :encoding "UTF-8"})
                   (clj->js)))))

(defn generate-deployment-fn [instructions]
  #(vec
    (for [instruction instructions]
      {:instruction instruction :result (exec instruction)})))

(defn generate-pre-deploy-fn [hooks]
  #(doseq [{:keys [hook-fn]} hooks]
     (hook-fn)))

(defn generate-deployment
  ([template]
   (generate-deployment (or (:weaver/context template) {}) (dissoc template :weaver/context)))
  ([context template]
   (let [config (assoc (weaver.core/process context template)
                       :albatross.weaver/context (update context
                                                         :config #(weaver.core/process-config
                                                                   (dissoc context :config) %))) ;;TODO: provide this magic from weaver, NOT here
         instructions (generate-instructions config)
         pre-deploy-hooks (generate-pre-deploy-hooks config)
         deployment-fn (generate-deployment-fn instructions)
         pre-deploy-fn (generate-pre-deploy-fn pre-deploy-hooks)
         post-deploy-hooks []
         post-deploy-fn #(println "WARNING: POST-DEPLOY ISN'T IMPLEMENTED")]
     {:pre-deploy-hooks pre-deploy-hooks
      :pre-deploy-fn pre-deploy-fn
      :instructions instructions
      :deployment-fn deployment-fn
      :post-deploy-hooks post-deploy-hooks ;; TODO: NOT IMPLEMENTED
      :post-deploy-fn    post-deploy-fn    ;; TODO: NOT IMPLEMENTED
      :run-deployment #(vector
                         (pre-deploy-fn)
                         (deployment-fn)
                         (post-deploy-fn))})))

;;TODO: add git and leiningen steps to albatross
;;TODO: compose all top level instruction sets
;;TODO: Maybe use specter either here, or in weaver, to escape processing for certain swaths of data

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  (.log js/console "Reloaded!"))
