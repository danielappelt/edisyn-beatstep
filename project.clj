(defproject edisyn-beatstep "0.1.0"
  :description "Edisyn service provider to support Arturia Beatstep"
  :url "https://github.com/danielappelt/edisyn-beatstep/"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [uk.co.xfactory-librarians/coremidi4j "1.1"]]
  :plugins [[lein-javac-resources "0.1.1"]]
  :hooks [leiningen.javac-resources]
  :source-paths ["src/clojure"]
  :java-source-paths ["src/edisyn"]
  :resource-paths ["resources"]
  :target-path "target/%s"
  :compile-path "%s/classes"
  :aot [edisyn.synth.arturiabeatstep.ArturiaBeatstep]
  :main edisyn.Edisyn
  :profiles {:compile {:resource-paths ^:replace ["compile-resources"]
                       :target-path "target/default"}
             :repl {:resource-paths ^:replace ["compile-resources"]
                    :main nil}
             :uberjar {:aot :all
                       :omit-source true
                       :resource-paths ^:replace []
                       :filespecs [{:type :paths :paths ["resources"]}]}})
