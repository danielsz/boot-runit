(ns danielsz.boot-runit
  {:boot/export-tasks true}
  (:require
   [clojure.java.io :as io]
   [boot.core       :as core]
   [boot.util       :as util]))


(core/deftask runit [e env FOO=BAR {kw edn} "The environment map"]
  (core/with-pre-wrap fileset
    (boot.util/info (str "environment " env "\n"))
    fileset))
