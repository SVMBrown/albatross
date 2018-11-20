(ns albatross.docker
  (:require [clojure.string :as string]))

(defn docker-build-instruction [image tags workspace]
  (str "docker build "
       " " (string/join " " (map
                             #(str "-t " image ":" %)
                             tags))
       " " workspace))

(defn docker-push-instruction
  ([image]
   (str "docker push " image))
  ([image tag]
   (str "docker push " image ":" tag)))

(defn docker-image-instructions
  "[repo-config image-config] -
   Compiles the instruction list for an image"
  [{url :url}
   {image-name :name
    tags :tags
    workspace :workspace}]
  (let [image (if (re-find (re-pattern (str "^" url)) image-name)
                image-name
                (str url image-name))]
    (into
     [(str "echo 'handling docker image: " image "'")
      (docker-build-instruction image (or (not-empty tags) []) workspace)
      (docker-push-instruction image)]
     (map (partial docker-push-instruction image) tags))))

(defn docker-login-instruction [{:keys [url user pass pass-file pass-env]}]
  (cond
    (not-empty pass-file)
    (str "cat " pass-file " | docker login --password-stdin -u " user " " url)

    (some? pass-env)
    (str "echo $" pass-env " | docker login --password-stdin -u " user " " url)

    :else
    (str "docker login -u " user " -p " pass " " url)))

(defn docker-logout-instruction [url]
  (str "docker logout " url))

(defn docker-repo-instructions
  "[repo-config] -
   Compiles the instruction list for a repo config"
  [{url :url
    user :user
    pass :pass
    pass-file :pass-file
    images :images
    :as repo-config}]
  (when-not (string? url)
    (throw (js/Error. ":docker step must have a valid (string) url")))
  (conj
   (into
    [(str "echo 'handling docker publishing step for repository: " url "'")
     (docker-login-instruction (select-keys repo-config [:url :user :pass :pass-file]))]
    (mapcat
     (partial docker-image-instructions
              repo-config)
     images))
   (docker-logout-instruction url)))


