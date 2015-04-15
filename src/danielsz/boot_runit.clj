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

(defn sanitize [key]
  (-> key
      (name)
      (.toUpperCase)
      (str/replace "-" "_")))

(defn write-executable [lines path]
  (io/make-parents path)
    (with-open [wrtr (io/writer path)]
      (doseq [line lines]
        (.write wrtr (str line "\n"))))
    (fs/chmod "+x" path))

(defn write-env [path env]
  (doseq [[key value] env]
    (let [filename (sanitize key)
          path (str/join "/" [path "env" filename])]
      (io/make-parents path)
      (spit path value))))

(defn write-logs-dir [path]
  (.mkdir (io/file (str/join "/" [path "logs"]))))

(defn write-app [app-path env]
  (debug "app path: " app-path)
  (debug "env: " env)
  (write-env app-path env)
  (write-logs-dir app-path))

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

(defn compute-paths [tmp options]
  (let [app-root (or (:app-root options) "/opt")
        service-root (or (:service-root options) "/etc/sv")
        group (:group options)
        name (:name options)
        app [app-root (or group "") name]
        service-name (if group
                       (str group "-" name)
                        name)
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
               (format "sudo mkdir -p %s" (:app paths))
               (format "sudo chown %s:%s %s"  user user (:app paths))
               (format "cp %s %s" jar-name (:app paths))
               (format "cp -R %s /" (str (:tmp paths) "/" (:app-root paths)))
               (format "sudo cp -R %s /etc" (str (:tmp paths) "/" (:service-root paths)))
               (format "sudo ln -s %s %s" (:service paths) (:runit paths))]]
        (write-executable lines (str (:tmp paths) "/commit.sh"))))

(core/deftask runit
  "Provides integration with runit, a UNIX init scheme with service supervision."
  [e env FOO=BAR {kw edn} "The environment map"
   a app-root APP str "Where user applications are installed, defaults to /opt"
   s service-root SRV str "Where runit services are installed, defaults to /etc/sv"
   n name NAME str "Name of application"
   g group GROUP str "Group segment"]
  (let [tmp (core/temp-dir!)
        paths (compute-paths tmp *opts*)]
    (util/info (str "opts: " *opts* "\npaths: " paths "\n"))
    (core/with-pre-wrap fileset
      (let [out-files (core/output-files fileset)
            jars  (pr-str (core/by-ext [".jar"] out-files))
            pom  (core/by-name ["pom.xml"] out-files)
            jar-name (str/join "-" [group name "standalone.jar"])]
        ; check if jars (a lazy sequence) contains the jar file of the project  if not  util/fail
        (if (seq pom)
          (do (util/info (str "jars: " jars "\nxmls: " (pr-str (first pom)) "\n"))
              (util/info (str (:project-symbol (extract-from-pom (io/file (:dir (first pom)) (:path (first pom))))))))
          (util/fail "Sorry. This task expects to find a pom.xml (which it didn't"))
        (write-app (:target-path paths) env)
        (spy :debug (write-service (:app paths) (:service-path paths) jar-name))
        (write-commit paths jar-name)
        (util/info "All done. You can now run commit.sh in target directory."))
      (-> fileset
          (core/add-resource tmp)
          core/commit!))))