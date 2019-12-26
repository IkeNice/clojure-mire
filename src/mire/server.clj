(ns mire.server
  (:use [mire.player]
        [mire.data :only [idPlayer newPlayer players-inventory]]
  						[mire.emojiList]
        [mire.commands :only [discard look execute]]
        [mire.rooms :only [add-rooms rooms]])
  (:use [clojure.java.io :only [reader writer]]
        [server.socket :only [create-server]])
  (:import (java.util TimerTask Timer))
)

(def money-all-players [])

(def finish-game? false)

(defn finish-game
	[]
	; (println @player-streams)
	(println "finish-game")
	(def all-money ())
	(doseq [info players-inventory]
		(println (info :name) " -> " @(info :money) " gold.")
		(def all-money (conj all-money @(info :money)))
	)
	(def max-money (apply max all-money))
	(def winers (filter #(= @(% :money) max-money) players-inventory))
	(doseq [winer winers]
		(println (winer :name) " is WINER!!!")
	)
)

(defn- cleanup
  [namePlayer]
  "Drop all inventory and remove player from room and player list."
  (dosync
   (doseq [item @*inventory*]
     (discard item))
   (commute player-streams dissoc *player-name*)
   (commute (:inhabitants @*current-room*)
            disj *player-name*)))

(defn- get-unique-player-name [name]
  (if (@player-streams name)
    (do (print "That name is in use; try again: ")
        (flush)
        (recur (read-line)))
    name))

(defn- mire-handle-client [in out]
  (binding [*in* (reader in)
            *out* (writer out)
            *err* (writer System/err)]

    ;; We have to nest this in another binding call instead of using
    ;; the one above so *in* and *out* will be bound to the socket
    (print "\nWhat is your name? ") (flush)

    (def player-name (get-unique-player-name (read-line)) )    ;; Устанавливаю переменной player-name имя игрока, введеное в консоли

    (newPlayer idPlayer player-name)

    (def id idPlayer)
    (def player-inventory ((first (filter #(= (% :id) id) players-inventory)) :inventory))
    (def player-money ((first (filter #(= (% :id) id) players-inventory)) :money))
    (binding [
              *player-id*  idPlayer
              *player-name*  player-name
              *current-room* (ref (@rooms :start))
              *inventory* player-inventory
              *money* player-money
              *current-emoji* (ref :no_emotion)
              *emoji-available* (ref #{:no_emotion :sad})]
      (dosync
       (commute (:inhabitants @*current-room*) conj *player-name*)
       (commute player-streams assoc *player-name* *out*))

      (println (look)) (print prompt) (flush)

      (try (loop [input (read-line)]
             (when input
             	 (if (not finish-game?)
	               (println (execute input))
	               (finish-game)
             	 )
               (.flush *err*)
               (print prompt) (flush)
               (recur (read-line))))
           (finally (cleanup))))))

(defn -main
  ([port dir]
     (add-rooms dir)
     (defonce server (create-server (Integer. port) mire-handle-client))
     (println "Launching Mire server on port" port)
     ; (for [x (range 100)]
     ;   (do
     ;     (println x)
     ;     (Thread/sleep 2000)
     ;   )
     ; )
		 (let [
		 				task(
		 					proxy [TimerTask] []
		 						(run [] 
		 							(do
		 								(def finish-game? true)
		 								(println "finish-game")
		 								(finish-game)
		 							)
		 						))
		 			]
		 		(. (new Timer) (schedule task (long 50000)))
		 )
  )
  ([port] (-main port "resources/rooms"))
  ([] (-main 3333)))
