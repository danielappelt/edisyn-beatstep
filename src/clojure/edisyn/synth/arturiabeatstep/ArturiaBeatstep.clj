(ns edisyn.synth.arturiabeatstep.ArturiaBeatstep
  (:import [edisyn.gui Category Chooser HBox LabelledDial VBox Style SynthPanel])
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

;; value 41 -> Global MIDI Channel
(def channels (reduce #(conj %1 {:label (str (+ %2 1)) :value %2})
                      [{:label "Global Channel" :value 0x41}] (range 16)))
;; See https://de.wikipedia.org/wiki/Musical_Instrument_Digital_Interface#Controller
(def cc-labels
  (let [msb-lsb ["Bank Select", "Modulation", "Breath Controller", "Undefined"
                 "Foot Controller", "Portamento Time", "Data Byte", "Main Volume"
                 "Balance", "Undefined", "Panorama", "Expression"
                 "Effect Control 1", "Effect Control 2", "Undefined", "Undefined"
                 "General Purpose Controller 1", "General Purpose Controller 2", "General Purpose Controller 3", "General Purpose Controller 4"
                 "Undefined", "Undefined", "Undefined", "Undefined"
                 "Undefined", "Undefined", "Undefined", "Undefined"
                 "Undefined", "Undefined", "Undefined", "Undefined"]]
    (into-array
     (map #(str %1 " - " %2)
          (range 128)
          (concat
           (apply concat (for [t ["MSB" "LSB"]] (map #(str % " " t) msb-lsb)))
           ["Hold 1", "Portamento", "Sostenuto", "Soft Pedal"
            "Legato Footswitch", "Hold 2", "Sound Controller 1 (Sound Variation)", "Sound Controller 2 (Harmonic Content)"
            "Sound Controller 3 (Release Time)", "Sound Controller 4 (Attack Time)", "Sound Controller 5 (Brightness)", "Sound Controller 6"
            "Sound Controller 7", "Sound Controller 8", "Sound Controller 9", "Sound Controller 10"
            "General Purpose Controller 5", "General Purpose Controller 6", "General Purpose Controller 7", "General Purpose Controller 8"
            "Portamento Control", "Undefined", "Undefined", "Undefined"
            "Undefined", "Undefined", "Undefined", "Effect 1 Depth"
            "Effect 2 Depth", "Effect 3 Depth", "Effect 4 Depth", "Effect 5 Depth"
            "Data Increment RPN/NRPN", "Data Decrement RPN/NRPN", "NRPN LSB", "NRPN MSB"
            "RPN LSB", "RPN MSB", "Undefined", "Undefined"
            "Undefined", "Undefined", "Undefined", "Undefined"
            "Undefined", "Undefined", "Undefined", "Undefined"
            "Undefined", "Undefined", "Undefined", "Undefined"
            "Undefined", "Undefined", "Undefined", "Undefined"
            "All Sounds Off", "Controller Reset", "Local Control On/Off"
            "All Notes Off",  "Omni Off", "Omni On", "Mono On / Poly Off"
            "Poly On / Mono Off"])))))

(def encoder-accelerations (into-array ["slow" "medium" "fast"]))
;; "full" only creates values 0 or 127
(def velocity-curves (into-array ["linear" "logarithmic" "exponential" "full"]))

(defn create-global [this color]
  (doto (HBox.)
    (.add (Chooser. "Global MIDI Channel" this (str 0x06 "_" 0x40) (into-array (map #(str (+ % 1)) (range 16)))))
    (.add (Chooser. "CV/Gate Channel" this (str 0x0c "_" 0x50)
                    (into-array (map :label channels))
                    (int-array (map :value channels)) nil))
    (.add (Chooser. "Encoder acceleration" this (str 0x04 "_" 0x41) encoder-accelerations))
    (.add (Chooser. "Pad velocity curve" this (str 0x03 "_" 0x41) velocity-curves))))

;; TODO: Add user scale settings
;; Note on:  0a 3d, 0a 39, 0a 31, 0a 21, 0a 01, 0b 3e, 0b 3c, 0b 38, 0b 30, 0b 20, 0b 00
;; Note off: 0a 03, 0a 07, 0a 0f, 0a 1f, 0a 3f, 0b 01, 0b 03, 0b 07, 0b 0f, 0b 1f, 0b 3f

(def encoder-types [{:label "Off", :value 0x00, :comps []}
                    {:label "CC", :value 0x01, :comps [:channel :cc :low :high :behaviour]}
                    {:label "RPN/NRPN", :value 0x04, :comps [:channel :coarse :lsb :msb :param-type]}])
;; TODO: low/high values are only relevant for Absolute mode
(def encoder-behaviour (into-array ["Absolute" "Relative (64)" "Relative (0)" "Relative (16)"]))
;; TODO: coarseness determines whether MSB or LSB of NRPN/RPN is sent?
(def encoder-coarseness {"Coarse" 0x06, "Fine" 0x26})
(def encoder-param-type (into-array ["NRPN" "RPN"]))

(defn create-encoder-comps [this index color]
  (let [comps {:channel (Chooser. "MIDI Channel" this (str index "_" 0x02)
                                  (into-array (map :label channels))
                                  (int-array (map :value channels)) nil)
               ;; Please note that, in general, the most restricted component per key will define the key's value range!
               :coarse (Chooser. "Coarse / Fine" this (str index "_" 0x03)
                                 (into-array (keys encoder-coarseness))
                                 (int-array (vals encoder-coarseness)) nil)
               :cc (Chooser. "CC" this (str index "_" 0x03) cc-labels)
               :low (LabelledDial. "Low Value" this (str index "_" 0x04) color 0 127)
               :lsb (LabelledDial. "LSB" this (str index "_" 0x04) color 0 127)
               :high (LabelledDial. "High Value" this (str index "_" 0x05) color 0 127)
               :msb (LabelledDial. "MSB" this (str index "_" 0x05) color 0 127)
               :param-type (Chooser. "Type" this (str index "_" 0x06) encoder-param-type)
               :behaviour (Chooser. "Behaviour" this (str index "_" 0x06) encoder-behaviour)}]
    ;; Update model value restrictions AFTER ui creation
    (.setMax (.getModel this) (str index "_" 0x03) 127)
    comps))

(def pad-types [{:label "Off", :value 0x00, :comps []}
                {:label "MMC", :value 0x07, :comps [:command]}
                {:label "CC", :value 0x08, :comps [:channel :cc :on :off :behaviour]}
                {:label "Silent CC", :value 0x01, :comps [:channel :cc :on :off :behaviour]}
                {:label "Note", :value 0x09, :comps [:channel :note :behaviour]}
                {:label "Program Change", :value 0x0B, :comps [:channel :prgchange :lsb :msb]}])
;; We added None/0 in order to have real values start from 1
(def mmc-commands (into-array ["None" "Stop" "Play" "Deferred Play" "FastForward"
                               "Rewind" "Record Strobe" "Record Exit" "Record Ready"
                               "Pause" "Eject" "Chase" "InList Reset"]))

(def pad-behaviour (into-array ["Toggle" "Gate"]))

;; Pad definition. Create pad parameter UIs per pad index
(defn create-pad-comps [this index color]
  {:channel (Chooser. "MIDI Channel" this (str index "_" 0x02)
                      (into-array (map :label channels))
                      (int-array (map :value channels)) nil)
   :command (Chooser. "MMC Command" this (str index "_" 0x03) mmc-commands)
   :cc (Chooser. "CC" this (str index "_" 0x03) cc-labels)
   :note (LabelledDial. "Note" this (str index "_"  0x03) color 0 127)
   :prgchange (LabelledDial. "Program Change" this (str index "_" 0x03) color 0 127)
   :off (LabelledDial. "Off Value" this (str index "_" 0x04) color 0 127)
   :lsb (LabelledDial. "Bank LSB" this (str index "_" 0x04) color 0 127)
   :on (LabelledDial. "On Value" this (str index "_" 0x05) color 0 127)
   :msb (LabelledDial. "Bank MSB" this (str index "_" 0x05) color 0 127)
   :behaviour (Chooser. "Behaviour" this (str index "_" 0x06) pad-behaviour)})

;; Display UI depending on chosen type
(defn create-type-ui [this label key types comps]
  (let [comps-box (HBox.)
        chooser (proxy [Chooser] ["Type" this key (into-array (map :label types)) (int-array (map :value types)) nil]
                  (update [key model]
                    ;; Each method fn takes an additional implicit first arg, which is bound to this.
                    (proxy-super update key model)
                    (.removeAll comps-box)
                    (dorun (for [c (:comps (types (.getIndex this)))] (.add comps-box (comps c))))
                    (.revalidate comps-box)
                    (.repaint this)))]
    (doto (HBox.)
      (.add (doto (VBox.)
              (.add (javax.swing.JLabel. label))
              (.add chooser)))
      (.add comps-box))))

(defn create-encoders [this color]
  (let [vbox (VBox.)]
    (dorun (for [index (range 16)]
             ;; 0x20-0x2F addresses one of the sixteen encoders
             (.add vbox (create-type-ui this (str "Encoder " (+ index 1))
                                        (str (+ 0x20 index) "_" 0x01)
                                        encoder-types
                                        (create-encoder-comps this (+ 0x20 index) color)))))
    vbox))

(defn create-pads [this color]
  (let [vbox (VBox.)]
    (dorun (for [index (range 16)]
             ;; 0x70-0x7F addresses one of the sixteen pads
             (.add vbox (create-type-ui this (str "Pad " (+ index 1))
                                        (str (+ 0x70 index) "_" 0x01)
                                        pad-types
                                        (create-pad-comps this (+ 0x70 index) color)))))
    vbox))

(defn create-buttons [this color]
  (let [vbox (VBox.)
        labels ["Start" "Stop" "Cntrl/Seq" "Ext. Sync" "Recall" "Store" "Shift" "Chan"]]
    (dorun (for [index (range (count labels))]
             (.add vbox (create-type-ui this (labels index)
                                        (str (+ 0x58 index) "_" 0x01)
                                        pad-types
                                        (create-pad-comps this (+ 0x58 index) color)))))
    vbox))

(defn beatstep-setup
  "This post init function for the namespace is used to implement the editor's construction."
  [this]
  (let [vbox (VBox.)]
    ;; Not all parameters on global tab seem to be saved in a Beatstep preset. It's more likely
    ;; that Beatstep has three independent patch categories: global, ctrl, seq?!
    (.add vbox (doto (Category. this (beatstep-getSynthName) (Style/COLOR_GLOBAL))
                 (.add (create-global this (Style/COLOR_GLOBAL)))))
    (.add vbox (doto (Category. this "Large encoder" (Style/COLOR_A))
                 (.add (create-type-ui this "Large encoder"
                                       (str 0x30 "_" 0x01)
                                       encoder-types
                                       (create-encoder-comps this 0x30 (Style/COLOR_A))))))
    (.setMax (.getModel this) (str 0x30 "_" 0x03) 127)
    (.add vbox (doto (Category. this "Function buttons" (Style/COLOR_A))
                 (.add (create-buttons this (Style/COLOR_A)))))
    (.addTab this "Global" (doto (SynthPanel. this)
                             (.add vbox java.awt.BorderLayout/CENTER))))
  (.addTab this "Encoder" (doto (SynthPanel. this)
                            (.add (doto (VBox.)
                                    (.add (doto (Category. this "Encoders" (Style/COLOR_B))
                                            (.add (create-encoders this (Style/COLOR_B)))) java.awt.BorderLayout/CENTER)))))
  (.addTab this "Pads" (doto (SynthPanel. this)
                         (.add (doto (VBox.)
                                 (.add (doto (Category. this "Pads" (Style/COLOR_C))
                                         (.add (create-pads this (Style/COLOR_C)))) java.awt.BorderLayout/CENTER))))))

;; For the sysex "specifiction" see
;; https://www.untergeek.de/2014/11/taming-arturias-beatstep-sysex-codes-for-programming-via-ipad/
(def sysex-prefix [0xF0 0x00 0x20 0x6B 0x7F 0x42])

;; Get F0 00 20 6B 7F 42 01 00 pp cc F7
(defn sysex-get-param [pp cc]
  ;; (println "get" (format "%02x %02x" pp cc))
  (concat sysex-prefix [0x01 0x00 pp cc 0xF7]))

;; Set F0 00 20 6B 7F 42 02 00 pp cc vv F7 (cc: number of the controller, pp: parameter, vv: value)
(defn sysex-set-param [pp cc vv]
  ;; (println "set" (format "%02x %02x %02x" pp cc vv))
  (concat sysex-prefix [0x02 0x00 pp cc vv 0xF7]))

(defn sysex-recall [number]
  (concat sysex-prefix [0x05 number 0xF7]))

(defn sysex-store [number]
  (concat sysex-prefix [0x06 number 0xF7]))

;; (defn beatstep-getSendsAllParametersInBulk [this] false)
(defn beatstep-getPauseBetweenMIDISends [this] 1)
;; TODO: maybe the following is not needed, or we need to adopt for it elsewhere
;; (defn beatstep-getNumSysexDumpsPerPatch [] (* 16 6))

;; Recognize sysex message bundle making up a complete beatstep patch -> byte[] data
(defn beatstep-recognize [data]
  ;; TODO: Check for BULK data at least using data length. 16 pads * 6 parameters * 12 bytes + x
  ;; (println "recognize:")
  ;; (println (seq data))
  (and (= (count data) (* 16 6 12)) (= (take 6 data) sysex-prefix)))

(defn beatstep-get-param [this key]
  (let [[cc pp] (map #(Integer/valueOf %) (clojure.string/split key #"_"))]
    (sysex-get-param pp cc)))

(defn beatstep-set-param [this key]
  (let [[cc pp] (map #(Integer/valueOf %) (clojure.string/split key #"_"))
        vv (.get (.getModel this) key)]
    (sysex-set-param pp cc vv)))

(defn beatstep-parseParameter [this data]
  ;; Set parameter F0 00 20 6B 7F 42 02 00 pp cc vv F7
  ;; pp parameter, cc pad/encoder, vv value
  ;; (println "parseParameter:")
  ;; (println (seq data))
  (let [[pp cc vv] (drop 8 data)]
    (.set (.getModel this) (str cc "_" pp) (int vv))))

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
  ;; TODO: maybe evaluate the return value of tryToSendSysex
  (.tryToSendSysex this (byte-array (sysex-recall (.get tempModel "number"))))
  (.simplePause this 10))

;; Equivalent of parseParameter. TODO: we provide the parameter type here so
;; that Clojure can distinguish it from other emit implementations.
(defn beatstep-emit-String [this key]
  (if (not= key "number") (byte-array (beatstep-set-param this key))))

(defn beatstep-emitAll-Model-boolean-boolean [this tempModel toWorkingMemory toFile]
  (object-array
   (map byte-array
        (reduce (fn [res key] (cons (beatstep-set-param this key) res))
                (cons (if tempModel (sysex-store (.get tempModel "number")) '()) '())
                (filter #(not= % "number") (.getKeys (.getModel this)))))))

(defn beatstep-performRequestCurrentDump [this]
  (dorun (for [key (.getKeys (.getModel this)) :while (not= key "number")]
           (.tryToSendSysex this (byte-array (beatstep-get-param this key))))))

(defn beatstep-performRequestDump [this tempModel changePatch]
  ;; We can ignore parameter changePatch here as we always need to change to the
  ;; requested patch first.
  (.performChangePatch this tempModel)
  (beatstep-performRequestCurrentDump this))
