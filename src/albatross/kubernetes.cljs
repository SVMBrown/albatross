(ns albatross.kubernetes
  (:require [clojure.string :as string]))

(defn set-kubeconfig-on-instructions [kubeconfig instructions]
  (mapv
   (fn [instruction]
     (-> instruction
         (string/replace #"--kubeconfig \S+" "")
         (string/replace #"kubectl " (str "kubectl --kubeconfig " kubeconfig " "))))
   instructions))

(defn use-context-instruction [context]
  (str "kubectl config use-context " context))

(defn action-config->action-type [{:keys [action-type]
                                   :as action-config}]
  (cond
    ;;Add potential custom dispatch/pattern recognition here
    :else
    action-type))

(defmulti action-instructions
  #'action-config->action-type)

(defmethod action-instructions :default [action-config]
  (do
    (println "Unrecognized action: '" (action-config->action-type action-config) "'\n\n action-config:\n\n" action-config)
    (throw (js/Error. (str "Unrecognized kubernetes action-type: " (action-config->action-type action-config) "\n\n action-config:\n\n" action-config "\n\n")))))

;; apply -f (Relies on weaver step happening BEFORE this one)
(defmethod action-instructions :apply-dir [{:keys [dir]
                                            :as action-config}]
  ;;NOTE: consider allowing weaver side-effect here instead of separate weaver step
  ;;    PROS: prevent de-normalization of data (i.e. connecting weaver-out to this dir name)
  ;;    CONS: side-effectful and potentially confusing for non-weaver use.
  [(str "echo 'applying k8s dir: " dir "'")
   (str "kubectl apply -f " dir)])

;; e.g. delete secret ... ; create secret generic <name> --from-file=<key>=<file> ...;
(defmethod action-instructions :secret [{secret-name :name
                                         keyname->file :config-map
                                         :as action-config}]
  [(str "echo 'deleting and recreating secret: " secret-name "'")
   (str "kubectl delete secret " secret-name " --ignore-not-found")
   (str "kubectl create secret generic " secret-name " "
        (string/join " "
                     (map
                      (fn [[keyname file]]
                        (str "--from-file=" keyname "=" file))
                      keyname->file)))])


(defn k8s-cluster-instructions [{:keys [kubeconfig
                                        use-context
                                        cluster-name
                                        actions]
                                 :as k8s-cluster-config}]
  (cond->
      [(str "echo 'handling cluster " (or cluster-name use-context "<unnamed cluster>") "'")]
    (not-empty use-context) (conj (use-context-instruction use-context))
    (not-empty actions)     (into (mapcat
                                   action-instructions
                                   actions))
    ;; If kubeconfig is set explicitly, set it on all kubectl commands (i.e. stateless) rather than setting a variable or something
    (not-empty kubeconfig)  (->> (set-kubeconfig-on-instructions kubeconfig))))

