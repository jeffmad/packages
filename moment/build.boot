(set-env!
  :resource-paths #{"resources"}
  :dependencies '[[adzerk/bootlaces   "0.1.9" :scope "test"]
                  [cljsjs/boot-cljsjs "0.4.3" :scope "test"]])

(require '[adzerk.bootlaces :refer :all]
         '[cljsjs.boot-cljsjs.packaging :refer :all])

(def +version+ "2.6.0-3")
(bootlaces! +version+)

(task-options!
  push {:ensure-clean false}
  pom  {:project     'cljsjs/moment
        :version     +version+
        :description "A javascript date library for parsing, validating, manipulating, and formatting dates."
        :url         "http://momentjs.com/"
        :license     {"MIT" "http://opensource.org/licenses/MIT"}
        :scm         {:url "https://github.com/cljsjs/packages"}})

(require '[boot.core :as c]
         '[boot.tmpdir :as tmpd]
         '[clojure.java.io :as io]
         '[clojure.string :as string])

(deftask generate-locale-deps []
  (let [tmp (c/temp-dir!)
        new-deps-file (io/file tmp "deps.cljs")
        fname->ns #(string/replace % #"_" "-")
        path->foreign-lib (fn [path]
                            (let [[_ lang] (re-matches #"cljsjs/common/lang/(.*)\.inc\.js" path)]
                              {:file path
                               :requires ["cljsjs.moment"]
                               :provides [(format "cljsjs.moment.lang.%s" (fname->ns lang))]}))]
    (with-pre-wrap
      fileset
      (let [existing-deps-file (first (->> fileset c/input-files (c/by-name ["deps.cljs"])))
            existing-deps      (read-string (slurp (tmpd/file existing-deps-file)))
            locale-files       (->> fileset c/input-files (c/by-re [#"^cljsjs/common/lang/.*"]) #_(c/by-ext [".inc.js"]))
            locales            (->> locale-files
                                    (map (comp path->foreign-lib tmpd/path)))
            new-deps (update-in existing-deps [:foreign-libs] concat locales)]
        (spit new-deps-file (pr-str new-deps))
        (-> fileset (c/add-resource tmp) c/commit!)))))

(deftask package []
  (comp
    (download :url "https://github.com/moment/moment/archive/2.6.0.zip"
              :checksum "0f9b226ff824066a2040056a4abfa0f6"
              :unzip true)
    ; Lang files are not immediately named .inc.js as we don't want deps-cljs to find them
    (sift :move {#"^moment-.*/moment\.js"          "cljsjs/development/moment.inc.js"
                 #"^moment-.*/min/moment\.min\.js" "cljsjs/production/moment.min.inc.js"
                 #"^moment-.*/lang/(.*)\.js"       "cljsjs/common/lang/$1.js"})
    ; I guess the foreign lib files should be named like clojure namespaces
    (sift :move {#"-" "_"})
    (sift :include #{#"^cljsjs"})
    (deps-cljs :name "cljsjs.moment")
    (sift :move {#"^cljsjs/common/lang/(.*)\.js" "cljsjs/common/lang/$1.inc.js"})
    (generate-locale-deps)))