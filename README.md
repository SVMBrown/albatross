# albatross

Albatross ([albatross-cljs](https://www.npmjs.com/package/albatross-cljs)) is a declarative deployment library and DSL.

## Overview

Albatross leverages [Weaver](https://github.com/SVMBrown/weaver#weaver) extensively to provide flexibility, concision, and code reuse.
An albatross deployment is specified as a template with pre-deploy, deploy, and post-deploy steps.
It is processed with the `albatross.core/generate-deployment` function, which returns a map.
This map has a function under `:run-deployment` which can be run to perform the entire deployment.

## Details

### Templating and weaver context
The context can be specified as `:weaver/context` at the top level of the map (must be a map literal, but I am considering supporting afilename as well), or as metadata in a similar manner to `figwheel-main` (e.g. `^{:config {...}}{:deployment ...}`).
If both are provided, they are merged, and the metadata takes precedence.
A context can also be passed explicitly to `albatross.core/generate-deployment` in its two arity version, in which case both metadata and `:weaver/context` are ignored.

The first thing that happens when `generate-deployment` is called is the templating of the deployment map.

Once the deployment map is templated, there are three major parts, which are top-level keys.
`:pre-deploy-hooks` which set up for the deployment, `:deployment` which specifies the actual deploying, and `:post-deploy-hooks` which manage actions post-deployment (TODO). We will cover these in detail a bit later.

### Deployment Steps

#### Deploy Hooks (:pre-deploy-hooks, :post-deploy-hooks)

NOTE: post-deploy-hooks are not implemented fully

Deploy hooks are effectful (usually non-deployment) steps that must be performed before or after a deployment. 
Examples include: generating files from templates, pulling from/pushing to Git, Setting environment variables, building an uberjar, etc.

The only supported hook type at the moment is the `:weaver` hook type.

##### :weaver

The weaver hook takes a map with `:templates` - a vector of templates, and `:context` - A context map which will be used by weaver.
See weaver for more detailed documentation on specific templating.

```clojure
{...
:pre-deploy-hooks [...
                   {:albatross/type :weaver
                    :context {:config {:environment "staging"
                                        :version :git/short-hash
                                        :app-name "my-service"
                                        :shortname "my-service"
                                        :external-port 30055
                                        :app-container-port 3000}}
                    :templates [{:from "k8s-edn/"
                                 :to   "target/k8s/json/"}]}
                                  
             ...]
...}
```

#### Main Deployment (:deployment)

Deployment is where the meat of the deployment happens. 
This is vector of maps which are each transformed into a vector of instructions (Only unix shell string commands are supported currently).
Each map returns a vector of one or more instructions which are then concatenated to form a complete deployment instruction set.
Deployment maps are parsed using the `deployment-instructions` multimethod, which can be extended as needed.
Note that the deployment step is NOT effectful. 
Only calling the generated `deployment-fn` or `run-deployment` functions will cause the _actual_ deployment to occur.
Deployment type is specified by the :albatross/type key on the instruction map.
Below are a list of deployment-instruction types supported by albatross.

##### :shell
This serves as an escape hatch to perform arbitrary shell instructions.
Should be used responsibly, as with any escape hatch.
Here's an example of a `:shell` configuration:
```clojure
{...
:deployment [...
             {:albatross/type :shell
              :exec ["echo foo" "pwd" "echo bar"]}
             ...]
...}
```

The `:exec` key can be either a string or a vector of strings. 
Like the entire albatross config, it can also include weaver templating.
The value of exec is either returned directly, or wrapped in a vector if necessary.

##### :docker
The docker deployment step represents deploying a set of docker images with a single connection to a single repository.
It is composed of a URL, credentials (`:user` key with username and either `:pass` for a literal password, or a `:pass-file` for piping in a password from a file into stdin), and `:images`, a vector of images.
Each image is a map with `:name` (the image's name), `:tags` (a list of desired tags), and `:workspace` (the directory containing the `Dockerfile`).
The `:docker` step will do the following:
  1. `docker login` using the provided credentials, connecting to the repository.
  2. For each image in `:images`, run `docker build` in the specified workspace, and `docker push` with the specified tags
  3. `docker logout` to close the session

Here's an example of a `:docker` configuration:


```clojure
{...
:deployment [...
             {:albatross/type :docker
              :url "docker.mycompany.tld:PORT/"
              :user :kw-env/DOCKER_USER ;;weaver => "jenkins"
              :pass :kw-env/DOCKER_PASS ;; weaver => "password123"
              :images [{:name :config/image-name ;; weaver => "myproject"
                        :tags ["staging-latest"]
                        :workspace "./"}]} 
             ...]
...}
```

##### :kubernetes
The kubernetes deployment step represents a set of actions (performed by `kubectl`) associated with a single context (i.e. KUBECONFIG).
The context can also be configured manually (e.g. by using environment variables or `:shell` steps) in which case the `:kubeconfig` and `:use-context` keys should be left blank.
There is a slight difference between `:kubeconfig` and `:use-context`.
They correspond to attaching a `--kubeconfig` flag to each `kubectl` command, and to running `kubectl config use-context <context>` before other commands respectively.
They can be used together, but this is generally unnecessary and has not been tested.
The kubernetes deployment step consists of a `:kubeconfig` and/or a `:use-context`, an optional, purely for logging `:cluster-name`, and `:actions`, a vector of k8s action maps.
Each action in `:actions` is handled by the `albatross.kubernetes/action-instructions` which can be extended as necessary. 
The types are as follows: 
  - `:apply-dir`, which is equivalent to running `kubectl apply -f <:dir>`.
  - `:secret`, this is equivalent to deleting and recreating a secret from a file or many files.
  
`:apply-dir` is intended to be used with the `:weaver` pre-deploy hook which generates JSON files from a source directory of EDN templates.
Since `apply` can be used to declaratively specify a set of kubernetes objects desired and allows kubernetes to manage the transition, it is the preferred method of deployment.
However, sometimes specific actions are required
`:secret` was added due to a specific need I had. It overwrites an existing secret from a file or a set of files.

Here's an example of a `:kubernetes` configuration:


```clojure
{...
:deployment [...
             {:albatross/type :kubernetes
              :use-context "my-context@kubernetes"
              :cluster-name "my-service"
              :actions [{:action-type :secret
                         :name "my-service-config"
                         :config-map {"app-config" "dev-config.edn"}}
                        {:action-type :apply-dir
                         :dir "target/foo/k8s/json/"}]}
             ...]
...}
```

### Return value
`weaver.core/generate-deployment` in and of itself doesn't have any side-effects. It returns a map of functions that perform various tasks, as well as data about these functions.
The keys are: `:config` `:pre-deploy-hooks` `:pre-deploy-fn` `:instructions` `:deployment-fn` `:post-deploy-hooks` `:post-deploy-fn` and `:run-deployment`

#### :config

This is the fully templated config map (i.e. post-weaver) that was used to generate this deployment.

#### :pre-deploy-hooks 

This is a vector of maps representing the generated pre-deploy hooks. Their shape depends on the hook type, but each will have a `:hook-fn` and a `:description`. 

#### :pre-deploy-fn

This is a function that runs all pre-deploy-hooks in order.


#### :instructions

This is a vector of strings (specifically bash commands) that comprise the deployment.

#### :deployment-fn

This is a function that executes all deployment instructions in order in a shell process

#### :post-deploy-hooks

TODO
see :pre-deploy-hooks, similar come *after* a successful deployment

#### :post-deploy-fn

TODO
see :pre-deploy-fn, similar but run *after* a successful deployment

#### :run-deployment

Runs pre-deploy-fn, then deployment-fn, then post-deploy-fn, returning the results in a vector




## Development

To get an interactive development environment run:

    lein fig:build

This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

	lein clean

To create a production build run:

	lein clean
	lein fig:min


## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
