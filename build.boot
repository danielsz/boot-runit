(set-env!
 :source-paths #{"src"}
 :resource-paths #{"src"}
 :dependencies '[[com.taoensso/timbre "4.1.4"]
                  [me.raynes/fs "1.4.6"]
                  [org.apache.maven/maven-model "3.2.5"]])

(task-options!
 pom {:project 'danielsz/boot-runit
      :version "0.1.0-SNAPSHOT"
      :scm {:name "git"
            :url "https://github.com/danielsz/boot-runit"}})

(deftask dev
  []
  (comp
   (repl :server true)
   (wait)))

(deftask build
  []
  (comp (pom) (jar) (install)))

(deftask push-release
  []
  (comp
   (build)
   (push :repo "clojars")))
