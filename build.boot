(set-env!
  :source-paths #{"src"}
  :dependencies '[[boot/core "2.0.0-rc14" :scope "provided"]
                  [adzerk/bootlaces "0.1.11" :scope "test"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.0.1")
(bootlaces! +version+)

(task-options!
 aot {:namespace '#{danielsz.boot-runit}}
 pom {:project 'danielsz/boot-runit
      :version +version+
      :scm {:name "git"
            :url "https://github.com/danielsz/boot-runit"}})

(deftask build
  "Build jar and install to local repo."
  []
  (comp (aot) (pom) (jar) (install)))
