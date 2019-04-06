;; export CLASSPATH=libraries/coremidi4j-1.1.jar:.; javac edisyn/*.java edisyn/*/*.java edisyn/*/*/*.java
;; export CLASSPATH=libraries/clojure-1.9.0.jar:libraries/coremidi4j-1.1.jar:libraries/spec.alpha-0.2.176.jar:.; java edisyn/Edisyn

;; TODO: Clojure uses hyphenated lowercase
(ns edisyn.synth.arturiabeatstep.ArturiaBeatstep
  (:import [edisyn.gui Category Chooser HBox LabelledDial VBox StringComponent Style SynthPanel])
  (:gen-class
   :extends edisyn.Synth
   :post-init setup
   :methods [^:static [getSynthName [] String]
             ^:static [recognize [bytes] boolean]
             ^:static [getNumSysexDumpsPerPatch [] int]]
   :prefix "beatstep-"))

(defn beatstep-getSynthName [] "Arturia Beatstep")
(defn beatstep-getHTMLResourceFileName [this] "ArturiaBeatstep.html")
;; TODO
;; (defn beatstep-getDefaultResourceFileName [this] "ArturiaBeatstep.init")

(def encoder-accelerations (into-array ["slow" "medium" "fast"]))
(def velocity-curves (into-array ["linear" "logarithmic" "exponential" "full"]))

;; Global MIDI channel – F0 00 20 6B 7F 42 02 00 50 0B nn F7 (MIDI channel-1, 0-15)
;; CV/Gate interface receive channel – F0 00 20 6B 7F 42 02 00 50 0C nn F7 (MIDI channel-1, 0-15)
;; Knob acceleration – F0 00 20 6B 7F 42 02 00  41 04 nn F7 (0=slow, 1=medium, 2=fast)
;; Pad velocity curve – F0 00 20 6B 7F 42 02 00  41 03 nn F7 (0=linear, 1=logarithmic, 2=exponential, 3=full – I prefer exponential).
(defn beatstep-create-global [this color]
  (doto (HBox.)
    ;; (.add (StringComponent. "Patch Name" this "name" 10 "Name must be up to 10 ASCII characters."))))
    (.add (LabelledDial. "MIDI Channel" this (str 0x0b "_" 0x50) color 0 16))
    (.add (LabelledDial. "CV/Gate Channel" this (str 0x0c "_" 0x50) color 0 16))
    (.add (Chooser. "Encoder acceleration" this (str 0x04 "_" 0x41) encoder-accelerations))
    (.add (Chooser. "Pad velocity curve" this (str 0x03 "_" 0x41) velocity-curves))))

;; 0=off, 1=Silent CC Switch, 7=MMC, 8=CC Switch, 9=Note, 0x0B=Program Change. 4 -> test NRPN/RPN
(def pad-types {"Off" []
                "MMC" [:command]
                "CC" [:channel :cc :on :off :behaviour]
                "Silent CC" [:channel :cc :on :off :behaviour]
                "Note" [:channel :note :behaviour]
                "Program Change" [:channel :prgchange :lsb :msb]})

;; Map numerical model values to sysex values by parameter pp
(def vv-map {0x01 [0x00 0x07 0x08 0x01 0x09 0x0B]})

(defn map-value [pp vv]
  ((or (vv-map pp) identity) vv))

(defn value-map [pp vv]
  (let [m (vv-map pp)]
    (if m (.indexOf m vv) (int vv))))

;; We added None/0 in order to have real values start from 1
(def mmc-commands (into-array ["None" "Stop" "Play" "Deferred Play" "FastForward"
                               "Rewind" "Record Strobe" "Record Exit" "Record Ready"
                               "Pause" "Eject" "Chase" "InList Reset"]))

(def pad-behaviour (into-array ["Toggle" "Gate"]))

;; Pad definition. Create pad parameter UIs per pad index
;; Set F0 00 20 6B 7F 42 02 00 pp cc vv F7 (cc is the number of the controller, pp parameter, vv value)
;; Get F0 00 20 6B 7F 42 01 00 pp cc F7
;; type 01, ch 02, mmc/cc/note/pc 03, off/lsb 04, on/msb 05, behaviour 06
(defn create-pad-comps [this index color]
  {:command (Chooser. "MMC Command" this (str index "_" 0x03) mmc-commands)
   :channel (LabelledDial. "MIDI Channel" this (str index "_" 0x02) color 0 16)
   :cc (LabelledDial. "CC" this (str index "_" 0x03) color 0 127)
   :on (LabelledDial. "On Value" this (str index "_" 0x05) color 0 127)
   :off (LabelledDial. "Off Value" this (str index "_" 0x04) color 0 127)
   :behaviour (Chooser. "Behaviour" this (str index "_" 0x06) pad-behaviour)
   :note (LabelledDial. "Note" this (str index "_"  0x03) color 0 127)
   :prgchange (LabelledDial. "Program Change" this (str index "_" 0x03) color 0 127)
   :lsb (LabelledDial. "Bank LSB" this (str index "_" 0x04) color 0 127)
   :msb (LabelledDial. "Bank MSB" this (str index "_" 0x05) color 0 127)})

;; Display UI depending on chosen type
(defn beatstep-create-pad [this color index get-name]
  ;; 0x70-0x7F addresses one of the sixteen pads
  (let [hbox (HBox.) comps (create-pad-comps this index color)] 
    ;; (dorun (for [c (pad-types "Note")] (.add hbox (comps c))))
    (doto (HBox.)
      (.add (doto (VBox.)
              (.add (javax.swing.JLabel. (get-name index)))
              (.add (proxy [Chooser] ["Type" this (str index "_" 0x01) (into-array (keys pad-types))]
                      (update [key model]
                        (proxy-super update key model)
                        (.removeAll hbox)
                        (dorun (for [c (pad-types (.getElement this (.get model key)))] (.add hbox (comps c))))
                        (.revalidate hbox)
                        (.repaint this))))))
      (.add hbox))))

(defn beatstep-create-pads [this color]
  (let [vbox (VBox.)]
    (dorun (for [index (range 16)]
             (doto vbox (.add (beatstep-create-pad this color (+ 0x70 index) #(str "Pad " (- % 0x6F)))))))
    vbox))

(def button-name {0x58 "Start", 0x59 "Stop", 0x5A "Cntrl/Seq", 0x5B "Ext. Sync", 0x5C "Recall", 0x5D "Store", 0x5E "Shift", 0x5F "Chan"})

(defn beatstep-create-buttons [this color]
  (let [vbox (VBox.)]
    (dorun (for [index (range 8)]
             (doto vbox (.add (beatstep-create-pad this color (+ 0x58 index) button-name)))))
    vbox))

(defn beatstep-setup [this]
  (let [vbox (VBox.)]
    (.add vbox (doto (Category. this (beatstep-getSynthName) (Style/COLOR_GLOBAL))
                 (.add (beatstep-create-global this (Style/COLOR_GLOBAL)))))
    (.add vbox (doto (Category. this "Function buttons" (Style/COLOR_A))
                 (.add (beatstep-create-buttons this (Style/COLOR_A)))))
    (.addTab this "Global" (doto (SynthPanel. this)
                             (.add vbox java.awt.BorderLayout/CENTER))))
  (.addTab this "Pads" (doto (SynthPanel. this)
                         (.add (doto (VBox.)
                                 (.add (beatstep-create-pads this (Style/COLOR_B)))) java.awt.BorderLayout/CENTER))))

;; For the sysex "specifiction" see
;; https://www.untergeek.de/2014/11/taming-arturias-beatstep-sysex-codes-for-programming-via-ipad/#Sysex_for_the_Pads
(def sysex-prefix [0xF0 0x00 0x20 0x6B 0x7F 0x42])

(defn sysex-get-param [pp cc]
  ;; (println "get" (format "%02x %02x" pp cc))
  (concat sysex-prefix [0x01 0x00 pp cc 0xF7]))

(defn sysex-set-param [pp cc vv]
  ;; (println "set" (format "%02x %02x %02x" pp cc vv))
  (concat sysex-prefix [0x02 0x00 pp cc vv 0xF7]))

(defn sysex-recall [number]
  ;; (println "recall" number)
  (concat sysex-prefix [0x05 number 0xF7]))

(defn sysex-store [number]
  ;; (println "store" number)
  (concat sysex-prefix [0x06 number 0xF7]))

;; (defn beatstep-getSendsAllParametersInBulk [this] false)
(defn beatstep-getPauseBetweenMIDISends [this] 1)
;; (defn beatstep-getPauseBetweenSysexFragments [this] 1) ;; ms
;; (defn beatstep-getSysexFragmentSize [this] 11)
;; TODO: maybe the following is not needed, or we need to adopt for it elsewhere
;; (defn beatstep-getNumSysexDumpsPerPatch [] (* 16 6))

;; Recognize sysex message bundle making up a complete beatstep patch -> byte[] data
(defn beatstep-recognize [data]
  ;; TODO: Check for BULK data - at least using data length. 16 pads * 6 parameters * 12 bytes
  (println "recognize:")
  (println (seq data))
  (and (= (count data) (* 16 6 12)) (= (take 6 data) sysex-prefix)))

(defn beatstep-get-param [this key]
  (let [[cc pp] (map #(Integer/valueOf %) (clojure.string/split key #"_"))]
    (sysex-get-param pp cc)))

(defn beatstep-set-param [this key]
  (let [[cc pp] (map #(Integer/valueOf %) (clojure.string/split key #"_")) vv (.get (.-model this) key)]
    ;; vv needs to be mapped to the correct sysex value from 0..n
    (sysex-set-param pp cc (map-value pp vv))))

(defn beatstep-parseParameter [this data]
  ;; Set parameter F0 00 20 6B 7F 42 02 00 pp cc vv F7
  ;; pp parameter, cc pad/encoder, vv value
  ;; vv needs to be mapped from sysex to the correct display value 0..n
  (println "parseParameter:")
  ;; (println (seq data))
  (let [[pp cc vv] (drop 8 data)]
    (println (str cc "_" pp) (value-map pp vv))
    (.set (.-model this) (str cc "_" pp) (value-map pp vv))))

(defn beatstep-parse [this data fromFile]
  (println "parse:")
  (println (seq data))
  ;; TODO dorun?
  (for [d (partition 12 data)] (beatstep-parseParameter this d))
  (.-PARSE_SUCCEEDED this))

(defn beatstep-gatherPatchInfo [this title tempModel writing]
  (let [combo (javax.swing.JComboBox. (into-array (map #(str "Preset " (+ % 1)) (range 15))))
        result (edisyn.Synth/showMultiOption this (into-array '("Preset")) (into-array (vector combo)) 
                                             title "Please select the preset to use.")]
    ;; Please note that internal preset numbers in Beatstep start from 1
    (if result (.set tempModel "number" (+ (.getSelectedIndex combo) 1)))
    result))

(defn beatstep-changePatch [this tempModel]
  ;; Recall patch using param "number" (0..15) from tempModel
  (println (seq (byte-array (sysex-recall (.get tempModel "number")))))
  (println (.tryToSendSysex this (byte-array (sysex-recall (.get tempModel "number")))))
  (.simplePause this 10))

;; Equivalent of parseParameter. TODO: we provide the parameter type here so
;; that Clojure can distinguish it from emit (re)defined below. Otherwise, we
;; could redefine emitAll(key, status) here in order to avoid Clojure confusing
;; the right function to pick.
(defn beatstep-emit-String [this key]
  (if (not= key "number") (byte-array (beatstep-set-param this key))))

(defn beatstep-emitAll-Model-boolean-boolean [this tempModel toWorkingMemory toFile]
  (object-array
   (map byte-array
        (reduce (fn [res key] (cons (beatstep-set-param this key) res))
                (cons (if tempModel (sysex-store (.get tempModel "number")) '()) '())
                (filter #(not= % "number") (.getKeys (.-model this)))))))

(defn beatstep-performRequestCurrentDump [this]
  (dorun (for [key (.getKeys (.-model this)) :while (not= key "number")]
           (.tryToSendSysex this (byte-array (beatstep-get-param this key))))))

(defn beatstep-performRequestDump [this tempModel changePatch]
  ;; We can ignore parameter changePatch here as we always need to change to the
  ;; requested patch first.
  (.performChangePatch this tempModel)
  (beatstep-performRequestCurrentDump this))
