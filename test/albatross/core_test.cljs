(ns albatross.core-test
    (:require
     [cljs.test :refer-macros [deftest is testing]]
     [albatross.core :as c]
     ;; OTHER TEST DIRS
     [albatross.deployment.docker-test]))

(def config-broad-scope {:deployment [{:albatross/type :shell
                                       :exec ["echo ':shell vector (p1)'"
                                              "pwd"]}
                                      {:albatross/type :shell
                                       :exec "echo ':shell string'"}
                                      {:albatross/type :shell
                                       :albatross/name "config1 testing .sh file execution"
                                       :exec "./dummy-script.sh 'foo' 'bar'"}
                                      {:albatross/name "(broad scope test config) docker"
                                       :albatross/type :docker
                                       :url "foo/"
                                       :user "a"
                                       :pass "b"
                                       :images
                                       [{:workspace "."
                                         :name "my/img"
                                         :tags ["foo" "bar" "baz"]}]}
                                      {:albatross/name "(broad scope test config) k8s"
                                       :albatross/type :kubernetes
                                       :kubeconfig "~/.kube/jenkins-staging-view.conf"
                                       :use-context "some-context"
                                       :cluster-name "Staging (uhn-va-clp-001s)"
                                       :actions
                                       [{:action-type :secret
                                         :name "fig-main-builds"
                                         :config-map {"dev" "./dev.cljs.edn"
                                                      "test" "./test.cljs.edn"}}
                                        {:action-type :apply-dir
                                         :dir "./k8s/"}]}]})

(def shell-template {:deployment [{:albatross/type :shell
                                   :exec ["echo ':shell vector (p1)'"
                                          "pwd"]}
                                  {:albatross/type :shell
                                   :exec "echo ':shell string'"}
                                  {:albatross/type :shell
                                   :exec [:fn/str "echo weaver ':kw-env/HOME' is '" :kw-env/HOME "'"]}
                                  {:albatross/type :shell
                                   :albatross/name "config1 testing .sh file execution"
                                   :exec "./dummy-script.sh 'foo' 'bar'"}]})

(deftest first-pass
  (let [instructions (c/generate-instructions config-broad-scope)]
    (is
     (vector? instructions)
     "Generated instructions must be a vector")
    (is
     (every? string? instructions)
     (apply str "Some instructions are not strings:\n"
            (interpose "\n" (map (fn [[idx val]] (str "(index: " idx "): " val)) (remove (comp string? second) (map-indexed vector instructions))))))))

(deftest shell-template-eval
  (let [{:keys [deployment-fn instructions]} (c/generate-deployment shell-template)]
    (is
     (= instructions (mapv :instruction (deployment-fn)))
     ":deployment-fn either failed or generates an instruction list inconsistent with instructions key")
    (is
     (vector? instructions)
     "Generated instructions must be a vector")
    (is
     (every? string? instructions)
     (apply str "Some instructions are not strings:\n"
            (interpose "\n" (map (fn [[idx val]] (str "(index: " idx "): " val)) (remove (comp string? second) (map-indexed vector instructions))))))))
