(ns mire.server
  (:use [mire.player]
        [mire.data :only [idPlayer newPlayer players-inventory]]
  						[mire.emojiList]
        [mire.commands :only [discard look execute]]
        [mire.rooms :only [add-rooms rooms]])
  (:use [clojure.java.io ];:only [reader writer]]
        [server.socket :only [create-server]]
        [clojure.core.async :only [thread-call]]
        ;[clj-tcp.client]
        
			) 						

	(:import 
											(java.lang String Thread)
											(java.net InetAddress ServerSocket  Socket SocketException)
           (java.io InputStreamReader OutputStream  PrintWriter StringReader StringWriter IOException PipedInputStream PipedOutputStream)
           )  
  (:require
    [immutant.web             :as web]
    [immutant.web.async       :as async]
    [immutant.web.middleware  :as web-middleware]
    [compojure.route          :as route]
    [environ.core             :refer (env)]
    [compojure.core           :refer (ANY GET defroutes)]
    [ring.util.response       :refer (response redirect content-type)]
    )
  (:gen-class)
 	
 )

(def port (int 3333))

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
    (print "\nWhat is your name? ") (print prompt) (flush) (.flush *out* )

    (def player-name (get-unique-player-name (read-line)) )    ;; Устанавливаю переменной player-name имя игрока, введеное в консоли

    (newPlayer idPlayer player-name)
    ( let [*in* System/out] (println player-name) )
    (def id idPlayer)
    (def player-inventory ((first (filter #(= (% :id) id) players-inventory)) :inventory))

    (binding [
              *player-id*  idPlayer
              *player-name*  player-name
              *current-room* (ref (@rooms :start))
              *inventory* player-inventory
              *current-emoji* (ref :no_emotion)
              *emoji-available* (ref #{:no_emotion :sad})]
      (dosync
       (commute (:inhabitants @*current-room*) conj *player-name*)
       (commute player-streams assoc *player-name* *out*))

      (println (look)) (print prompt) (flush)

      (try (loop [input (read-line)]
             (when input
               (println (execute input))
               ;(binding [*out* System/out] (println "im here"))
               (.flush *err*)
               (print prompt) (flush)
               (.flush *out* )
               (recur (read-line))))
           (finally (cleanup))))))


;==server=functions
; WEB SOCKET CONNECTION HANDLER
(defn chars-to-string
[chars]
"translate chars vector to String"
(apply str (map char (reverse (into [] chars) )))
)

(defn complete-message
	[msg]
	"true if messege is complete"
	(do
	(= (last (butlast msg) ) (first prompt))
	)
	

)
(defn send-messeges-to-client
[client]
()

)
(defn send-messeges-to-clients
			[]
			"send messeges to clients"
			(dosync
					(println "start to send messages to clients")
					(doseq [elem  @connections]
							( do
									(.start(.Thread (fn []
									(let  [reader (last(last elem))
																channel (first elem)
																outp (ref '() )
																]
									(loop []
																			(while (not= (.available reader) 0)
																					(dosync
																							( commute outp conj  (.read reader))
																					)
																	 	)
																			(when (not (complete-message  (chars-to-string @outp) ) )
																			(Thread/sleep 10) (recur) )
										)	
																			(async/send! channel ( chars-to-string @outp))
																			
									) (Thread/sleep 10) (recur) )

							)))		
							
					)
			)
			;(recur)
)

(def websocket-callbacks
  "WebSocket callback functions"
  {:on-open   (fn [channel] ;; When socket connection opens
  	( 
  			dosync
  			;(Thread/sleep 1000)

  	(let [
  	 				;str (String. "Bob\nsouth"
  	 				ins  (PipedInputStream. 	 )
  	 				pip_writer (PipedOutputStream. ins) 	
        pip_reader (PipedInputStream.)
        outs  (PipedOutputStream. pip_reader )  ;(.getOutputStream s)
        fun mire-handle-client
        ] 
  	(do
						                  
						                
						                 (.start (Thread. (fn[] ( ;(fun in out)
																				     let [ in ins
																				     							out outs](
																				     							mire-handle-client in out)
																				     ))))
						                 (dosync (commute connections conj  {channel [ pip_writer  pip_reader]} )  )
						                 	(println "New web connection")
						                 	;(send-messeges-to-clients)

   		)
  	)
  )	
 )		


   
  

  :on-close   (fn [ch {:keys [code reason]}]
    (
    		do
    		  		(println "close code:" code "reason:" reason)
    						
    						(dosync
    									
    									(if (@connections ch) 
    												(let [writer (first (@connections ch))
    																		reader (last (@connections ch))]
    												(.close writer)
    												(.close reader))
    												(println "ERROR on-close")	
    									)

    									
    							)
    		)

    )
  :on-message (fn [ch m] 
 			
  			(dosync	
  					 (if (@connections ch)
  					 (let [pip_writer  ( first (@connections ch ) )
            ]
          ;  (if (not= m "reload")
										(do 
						  				(. pip_writer write (.getBytes (str m "\n")))
						  				(. pip_writer flush)
						  			;(send-messeges-to-clients)
										)
										;(send-messeges-to-clients)    
									;)	       

        )
        (println "error sending to client")
        )    
  				
  			)
  )  
    
})


(defroutes routes
  (GET "/" {c :context} (redirect (str c "/index.html")))
  (route/resources "/"))


;=================

(defn -main
  ([& {:as args}]
  		(	let [ port 3333 
  										dir "resources/rooms"]
		     (add-rooms dir)
		     (defonce server (create-server (Integer. port) mire-handle-client))
		     (println "Launching Mire server on port" port)
		    
		   )
		     (web/run
		    			(-> routes
		      			(web-middleware/wrap-session {:timeout 20})
		     			 ;; wrap the handler with websocket support
		      			;; websocket requests will go to the callbacks, ring requests to the handler
		      		(web-middleware/wrap-websocket websocket-callbacks))
		      		(let [ host "0.0.0.0"
		      									port "5000"	]
		      			(merge {"host" host, "port" port } args) 
		      		)
		     )
		     (.start(Thread. send-messeges-to-clients ))		
		     
		     (for [x (range 100)]
		       (do
		         (println x)
		         (Thread/sleep 2000)
		       )
		     )
		   
  )
  ;([port] (-main port "resources/rooms"))
  ;([] (-main 3333))
  )
