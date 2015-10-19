(ns danielsz.boot-runit
  {:boot/export-tasks true}
  (:require
   [danielsz.pom-helpers :refer [extract-from-pom]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [boot.core       :as core]
   [boot.util       :as util]
   [boot.task.built-in :refer [pom]]
   [me.raynes.fs :as fs]
   [taoensso.timbre :as timbre])
  (:import (org.apache.maven.model.io.xpp3 MavenXpp3Reader)))

(timbre/refer-timbre)

;; utils

(defn sanitize [key]
  (-> key
      (name)
      (.toUpperCase)
      (str/replace "-" "_")))

(defn to-java-properties [env]
  (let [transform #(-> %
                       name
                       (str/replace "-" ".")
                       (str "=")
                       (->> (str "-D")))]
    (map transform (keys env))))

(defn try-it-out [app-path jar-file env]
  (let [env-switches (str/join " " (map str/join (zipmap (to-java-properties env) (vals env))))]
    (str "java -jar -server " env-switches " " app-path "/" jar-file)))

(defn write-executable [lines path]
  (io/make-parents path)
    (with-open [wrtr (io/writer path)]
      (doseq [line lines]
        (.write wrtr (str line "\n"))))
    (fs/chmod "+x" path)) ; fileset is immutable in boot, has no effect

(defn write-env [path env]
  (doseq [[key value] env]
    (let [filename (sanitize key)
          path (str/join "/" [path "env" filename])]
      (io/make-parents path)
      (spit path value))))

(defn write-logs-dir [path]
  (.mkdir (io/file (str/join "/" [path "logs"]))))

(defn write-app [app-path env]
  (write-env app-path env)
  ;; fileset in boot, empty dir has no effect
  ;(write-logs-dir app-path)
  )

(defn write-run-service [user app-path service-path jar-filename]
  (let [lines ["#!/bin/sh -e"
               (str "BASE_DIR=" app-path)
               (str "JAR=" jar-filename)
               "exec 2>&1"
               (str "exec chpst -u " user " -e $BASE_DIR/env java -jar -server $BASE_DIR/$JAR")]
        path (str service-path "/run")]
    (write-executable lines path)))

(defn write-run-log [user app-path service-path]
  (let [lines ["#!/bin/sh -e"
               (str "BASE_DIR=" app-path)
               (str "exec chpst -u " user " svlogd -tt $BASE_DIR/logs")]
        path (str service-path "/log/run")]
    (write-executable lines path)))

(defn write-service [app-path service-path jar-name]
  (let [user (System/getProperty "user.name")]
    (write-run-service user app-path service-path jar-name)
    (write-run-log user app-path service-path)))

(defn assemble-path [els]
  (-> (str/join "/" els)
      (clojure.string/replace #"(?<!http:)//" "/")))

(defn compute-paths [tmp options pom]
  (let [app-root (or (:app-root options) "/opt")
        service-root (or (:service-root options) "/etc/sv")
        artifact (:artifact pom)
        group (:group pom)
        app [app-root (if (= group artifact) "" group) artifact]
        service-name (if (= group artifact)
                       artifact
                       (str group "-" artifact))
        service [service-root service-name]
        runit ["/etc/service" service-name]
        target-path (conj (seq app) tmp)
        service-path (conj (seq service) tmp)
        paths (zipmap [:app :service :target-path :service-path :runit]
                      (map assemble-path [app service target-path service-path runit]))]
    (assoc paths :app-root app-root :service-root service-root :tmp tmp)))

(defn write-commit [paths jar-name]
  (let [user (System/getProperty "user.name")
        lines ["#!/bin/sh -e"
               "find ./etc -name run | xargs chmod u+x" ;https://github.com/boot-clj/boot/pull/196
               (format "sudo mkdir -p %s" (str (:app paths) "/logs"))
               (format "sudo chown %s:%s %s"  user user (:app paths))
               (format "cp %s %s" jar-name (:app paths))
               (format "cp -R %s /" (str "." (:app-root paths)))
               (format "sudo cp -R %s /etc" (str "." (:service-root paths)))
               (format "sudo ln -sfn %s %s" (:service paths) (:runit paths))]] 
        (write-executable lines (str (:tmp paths) "/commit.sh"))))

(core/deftask runit
  "Provides integration with runit, a UNIX init scheme with service supervision. This task makes the assumption that you're deploying an uberjar."
  [e env FOO=BAR {kw edn} "The environment map"
   a app-root APP str "Where user applications are installed, defaults to /opt"
   s service-root SRV str "Where runit services are installed, defaults to /etc/sv"]
  (let [tmp (core/tmp-dir!)]
    (core/with-pre-wrap fileset
      (let [out-files (core/output-files fileset)
            pom  (core/by-name ["pom.xml"] out-files)]
        (if (seq pom)
          (do 
            (let [model (extract-from-pom (io/file (:dir (first pom)) (:path (first pom))))
                  paths (compute-paths tmp *opts* model)
                  jar-name (str (:artifact model) "-" (:version model) ".jar")]
              (util/info (str  "Preparing deployment script for " jar-name ".\n"))
              (write-app (:target-path paths) env)
              (write-service (:app paths) (:service-path paths) jar-name)
              (write-commit paths jar-name)
              (util/info "All done. You can now run commit.sh in target directory.\n")
              (util/info "You may want to test the jar manually on the command line.\n")
              (util/info (str (try-it-out (:app paths) jar-name env) "\n"))))
          (do
            (util/fail "Sorry. This task expects to find a pom.xml (which it didn't).\n")
            (*usage*))))
      (-> fileset
          (core/add-resource tmp)
          core/commit!))))
