(ns mire.player)

(def ^:dynamic *current-room*)
(def ^:dynamic *inventory*)
(def ^:dynamic *player-name*)
(def ^:dynamic *player-id*)


(def prompt "> ")
(def player-streams (ref {}))

(defn carrying?
  [thing
;;    player-inventory
   ]

;;   (some #{(keyword thing)} @player-inventory)
  (some #{(keyword thing)} @*inventory*)
  )
