(set-env!
 :source-paths #{"src"}
 :resource-paths #{"src"}
 :dependencies '[[me.raynes/fs "1.4.6"]
                 [org.apache.maven/maven-model "3.2.5"]])

(task-options!
 pom {:project 'danielsz/boot-runit
      :version "0.1.0-SNAPSHOT"
      :scm {:name "git"
            :url "https://github.com/danielsz/boot-runit"}}
 push {:repo-map {:url "https://clojars.org/repo/"}})

(require '[danielsz.boot-runit :refer [runit]])

(deftask dev
  "Run a restartable system in the Repl"
  []
  (comp
   (watch :verbose true)
   (notify :visual true)
   (repl :server true)))


(deftask test-run
  []
  (comp (pom :project (symbol "zuby") :version "0.O.1")
        (runit :restart true :out-of-memory true :project "zuby" :ulimit "-n 100000")
        (target)))

(deftask build
  []
  (comp (pom) (jar) (install)))

(deftask push-release
  []
  (comp
   (build)
   (push)))
