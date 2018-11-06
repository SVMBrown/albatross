(ns albatross.kubernetes
  (:require [clojure.string :as string]))

(defn set-kubeconfig-on-instructions [kubeconfig instructions]
  (mapv
   (fn [instruction]
     (-> instruction
         (string/replace #"--kubeconfig \S+" "")
         (string/replace #"kubectl " (str "kubectl --kubeconfig " kubeconfig))))
   instructions))

(defn use-context-instruction [context]
  (str "kubectl config use-context " context))

(defn k8s-cluster-instructions [{:keys [kubeconfig
                                        use-context
                                        cluster-name
                                        actions]
                                 :as k8s-cluster-config}]
  (cond->
      [(str "echo 'handling cluster " (or cluster-name use-context "<unnamed cluster>") "'")]
    (not-empty use-context) (conj (use-context-instruction use-context))
    ;;TODO: transform vector of k8s ops into concatted list of commands (e.g. manual creation vs hydrated configs w/ 'kubectl apply -f' or other means of apply)
    (not-empty kubeconfig) (->> (set-kubeconfig-on-instructions kubeconfig))))

