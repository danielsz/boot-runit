(set-env!
  :source-paths #{"src"}
  :dependencies '[[adzerk/bootlaces "0.1.12" :scope "test"]
                  [com.taoensso/timbre "4.1.4"]
                  [me.raynes/fs "1.4.6"]
                  [org.apache.maven/maven-model "3.2.5"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.0.5")
(bootlaces! +version+)

(task-options!
 aot {:namespace '#{danielsz.boot-runit}}
 pom {:project 'danielsz/boot-runit
      :version +version+
      :scm {:name "git"
            :url "https://github.com/danielsz/boot-runit"}})

(deftask dev
  []
  (comp
   (repl :server true)
   (wait)))
